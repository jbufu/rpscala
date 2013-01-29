package com.janrain.rpscala

import org.scalatra._
import scalate.ScalateSupport
import java.util.Properties
import java.io.FileInputStream
import scala.collection.JavaConversions._
import org.openid4java.consumer.ConsumerManager
import RP._
import org.openid4java.message.ParameterList
import javax.servlet.http.HttpServletRequest

object RP {

  final val CONFIG_PATH = System.getProperty("CONFIG_PATH")
  final val CONFIG_FILE = "config_file"
  final val CONFIG_NONE = "NO_CONFIG"

  final val LOGIN_PATH = "/login"
  final val RETURN_PATH = "/return"

  final val OPENID_IDENTIFIER = "openid_identifier"
  final val OPENID_REQUEST_EXTENSIONS = "openid_request_extensions"

  final val OPENID_REQUEST = "openid_request"
  final val OP_ENDPOINT = "op_endpoint"

  final val VERIFIED_ID = "verified_id"
  final val VERIFIED_MSG = "verified_msg"
  final val AUTH_RESPONSE = "auth_response"

  final val openid = new ConsumerManager
}

class RP extends ScalatraServlet with ScalateSupport {

  get("/") {
    redirect(LOGIN_PATH)
  }

  get(LOGIN_PATH + "/?:config?") {
    contentType = "text/html"
    val config = params.getOrElse("config", CONFIG_NONE )
    val requestParams = loadConfig(config)
    ssp("/login",
      OPENID_IDENTIFIER -> requestParams.get(OPENID_IDENTIFIER).getOrElse(""),
      OPENID_REQUEST_EXTENSIONS -> kvString(requestParams.filterKeys(_ != OPENID_IDENTIFIER)),
      CONFIG_FILE -> configFilePath(config)
    )
  }

  post(LOGIN_PATH + "/*") {
    contentType = "text/html"
    val userSuppliedId = params(OPENID_IDENTIFIER)
    val openidRequestExtensionParams = params.getOrElse(OPENID_REQUEST_EXTENSIONS, "")
    val req = openidRequest(Some(userSuppliedId), returnUrl(baseUrl(request.getRequestURL.toString)))
    layoutTemplate("/WEB-INF/layouts/formpost.ssp",
      OPENID_REQUEST -> (req.getParameterMap ++ fromKVString(openidRequestExtensionParams).map(kv => ("openid." + kv._1, kv._2))).toList,
      OP_ENDPOINT -> req.getOPEndpoint
    )
  }

  get(RETURN_PATH) {
    contentType = "text/html"
    val verif = openidVerify(request)
    ssp("response",
      VERIFIED_ID -> (if (verif.getVerifiedId != null) verif.getVerifiedId.getIdentifier else "[no verified identifier]"),
      VERIFIED_MSG -> (if (verif.getStatusMsg != null) verif.getStatusMsg else "[no message]"),
      AUTH_RESPONSE -> kvString(verif.getAuthResponse.getParameterMap.map(kv => (kv._1.toString -> kv._2.toString)).toMap)
    )
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }

  private def configFilePath(configName: String) =
    if (CONFIG_NONE == configName) CONFIG_NONE
    else CONFIG_PATH + "/" + configName + ".properties"

  private def loadConfig(configName: String): Map[String,String] = {
    if (CONFIG_NONE == configName)
      return Map.empty
    try {
      val config = new Properties
      config.load(new FileInputStream(configFilePath(configName)))
      config.toMap
    } catch {
      case e: Exception => halt(400, "config missing: " + configFilePath(configName))
    }
  }

  private def kvString(m: Map[String,String]) = m.map( kv => kv._1 + "=" + kv._2).mkString("\n")

  private def fromKVString(kvString: String): Map[String,String] =
    kvString.split("\n").map(line =>
      (line.trim.takeWhile(_ != '='),
       line.trim.dropWhile(_ != '=').drop(1))
    ).toMap

  private def baseUrl(requestUrl: String) = requestUrl.replaceAll(LOGIN_PATH + ".*", "")
  private def returnUrl(rpBaseUrl :String) = rpBaseUrl + RETURN_PATH
  private def receivingUrl(request: HttpServletRequest) =
    request.getRequestURL.append(if (request.getQueryString.length > 0) "?" + request.getQueryString else "").toString

  private def openidRequest(identifier: Option[String], returnUrl: String) =
    openid.authenticate(openid.discover(identifier.getOrElse(halt(400, "invalid identifier"))), returnUrl)

  def openidVerify(request: HttpServletRequest) = {
    openid.verify(receivingUrl(request), new ParameterList(request.getParameterMap), null )
  }
}
