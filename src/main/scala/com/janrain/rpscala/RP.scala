package com.janrain.rpscala

import org.scalatra._
import scalate.ScalateSupport
import java.util.Properties
import java.io.FileInputStream
import scala.collection.JavaConversions._
import org.openid4java.consumer.ConsumerManager
import RP._
import org.openid4java.message.{Message, ParameterList}
import javax.servlet.http.HttpServletRequest
import collection.immutable.TreeMap
import org.openid4java.discovery.DiscoveryInformation
import java.net.URLEncoder
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod

object RP {

  final val CONFIG_PATH = System.getProperty("CONFIG_PATH")
  final val CONFIG_FILE = "config_file"
  final val CONFIG_NONE = "NO_CONFIG"

  final val OPENID_LOGIN_PATH = "/login"
  final val OPENID_RETURN_PATH = "/return"

  final val ENGAGE_LOGIN_PATH = "/engage"
  final val ENGAGE_APP_NAME = "engage_app_name"
  final val ENGAGE_API_KEY = "engage_api_key"
  final val ENGAGE_RP_TOKEN_URL = "engage_rp_token_url"

  final val OPENID_VERSION = "openid_version"
  final val OPENID_IMMEDIATE = "openid_immediate"
  final val OPENID_REDIRECT = "openid_redirect"
  final val OPENID_IDENTIFIER = "openid_identifier"
  final val FILTERED_PARAMS = Seq(OPENID_VERSION, OPENID_IDENTIFIER)

  final val OPENID_REQUEST_EXTENSIONS = "openid_request_extensions"

  final val OPENID_REQUEST = "openid_request"
  final val OP_ENDPOINT = "op_endpoint"

  final val VERIFIED_ID = "verified_id"
  final val EXTENSIONS = "extensions"
  final val VERIFIED_MSG = "verified_msg"
  final val AUTH_RESPONSE = "auth_response"

  final val openid = new ConsumerManager
  // openid.setMaxAssocAttempts(0)
  // openid.setAllowStateless(true)
}

class RP extends ScalatraServlet with ScalateSupport with Loggable {

  get("/") {
    redirect(OPENID_LOGIN_PATH)
  }

  get((OPENID_LOGIN_PATH + "/?(.*)").r) {
    contentType = "text/html"
    val configs = multiParams("captures").flatMap(_.split("/")).map(c => if (c == "") CONFIG_NONE else c)
    val requestParams = configs.foldLeft(Map.empty[String,String])( (loaded,configName) => loaded ++ loadConfig(configName)  )
    ssp("/login",
      OPENID_VERSION -> requestParams.get(OPENID_VERSION).getOrElse(""),
      OPENID_IDENTIFIER -> requestParams.get(OPENID_IDENTIFIER).getOrElse(""),
      OPENID_REQUEST_EXTENSIONS -> kvString(requestParams.filterKeys(! FILTERED_PARAMS.contains(_))),
      CONFIG_FILE -> configFilePath(configs.mkString(", "))
    )
  }

  post(OPENID_LOGIN_PATH + "/*") {
    contentType = "text/html"
    val userSuppliedId = params(OPENID_IDENTIFIER)
    val versions = params(OPENID_VERSION)
    val immediate = params.get(OPENID_IMMEDIATE).getOrElse("false").toBoolean
    val redir = params.get(OPENID_REDIRECT).getOrElse("false").toBoolean
    val openidRequestExtensionParams = params.getOrElse(OPENID_REQUEST_EXTENSIONS, "").trim
    val req = openidRequest(userSuppliedId, versions, immediate, returnUrl(baseUrl(request.getRequestURL.toString)))
    val openid_request = req.getParameterMap ++ fromKVString(openidRequestExtensionParams).map(kv => ("openid." + kv._1, kv._2))
    logger.info("\nopenid request:\n\t%s".format(openid_request.mkString("\n\t")))
    if (redir)
      redirect(req.getOPEndpoint + "?" + openid_request.map(kv => URLEncoder.encode(kv._1.toString, "utf-8") + "=" + URLEncoder.encode(kv._2.toString, "utf-8")).mkString("&"))
    else
      layoutTemplate("/WEB-INF/layouts/formpost.ssp",
        OPENID_REQUEST -> openid_request.toList,
        OP_ENDPOINT -> req.getOPEndpoint
      )
  }

  post(OPENID_RETURN_PATH) {
    doReturnUrl()
  }

  get(OPENID_RETURN_PATH) {
    doReturnUrl()
  }

  // ======================= ENGAGE-POWERED RP ============================================================

  final val HTTP_CLIENT = new HttpClient()

  val ENGAGE_AUTH_INFO = "https://rpxnow.com/api/v2/auth_info"
  val ENGAGE_RESPONSE = "engage_response"

  get(ENGAGE_LOGIN_PATH + "/:" + ENGAGE_APP_NAME) {
    contentType = "text/html"
    val engageAppName = params(ENGAGE_APP_NAME)
    ssp( "engage1",
      (ENGAGE_APP_NAME, engageAppName.toString),
      (ENGAGE_RP_TOKEN_URL, request.getRequestURL.toString)
    )
  }

  post(ENGAGE_LOGIN_PATH + "/:" + ENGAGE_APP_NAME) {
    contentType = "text/html"
    val engageAppName = params(ENGAGE_APP_NAME)
    val engageConfigParams = loadConfig(engageAppName)
    val apiKey = engageConfigParams.getOrElse(ENGAGE_API_KEY,
      throw new Exception("%s missing from engage configuration '%s'".format(ENGAGE_API_KEY, engageAppName)))
    val post = new PostMethod(ENGAGE_AUTH_INFO)
    post.setParameter("token", params("token"))
    post.setParameter("apiKey", apiKey)
    try {
      val statusCode = HTTP_CLIENT.executeMethod(post)
      logger.info("[ " + ENGAGE_AUTH_INFO + " ] replied: status code=" + statusCode + "\n" + post.getResponseBodyAsString)
      if (200 == statusCode)
        ssp("engage2", ENGAGE_RESPONSE -> post.getResponseBodyAsString)
      else
        halt(500, "OPX returned status code: " + statusCode + ", response body " + post.getResponseBodyAsString)
    } catch {
      case e: Exception => halt(500, "OPX callback error: " + e.getMessage)
    }
  }

  // ======================= / ENGAGE-POWERED RP ============================================================

  private def doReturnUrl() = {
    contentType = "text/html"
    val responseModel = openidVerify(request)
    ssp("response", responseModel: _*)
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
    (for {
      line <- kvString.split("\n")
      if line.length > 0
    } yield {
      (line.trim.takeWhile(_ != '='),
       line.trim.dropWhile(_ != '=').drop(1))
    }).toMap

  private def baseUrl(requestUrl: String) = requestUrl.replaceAll(OPENID_LOGIN_PATH + ".*", "")
  private def returnUrl(rpBaseUrl :String) = rpBaseUrl + OPENID_RETURN_PATH
  private def receivingUrl(request: HttpServletRequest) =
    request.getRequestURL.append(
      if (request.getQueryString != null && request.getQueryString.length > 0) "?" + request.getQueryString else "")
      .toString

  private def extensionsAsStrings(message: Message): List[String] = {
    if (message == null || message.getExtensions.size < 1)
      return Nil

    val extAliases = message.getExtensions.map(extTypeUri => "openid." + message.getExtensionAlias(extTypeUri.toString))

    val respCore = kvString(TreeMap(message.getParameterMap
      .withFilter(kv => ! extAliases.exists( kv._1.toString.startsWith ))
      .map(kv => (kv._1.toString,kv._2.toString)).toList: _*
    ))

    val respExtensions: List[String] = message.getExtensions.map(extTypeUri => {
      message.getExtension(extTypeUri.toString).getParameters.toString.split("\n").sorted.mkString("\n")
    }).toList

    respCore :: respExtensions
  }

  private def openidRequest(identifier: String, version: String, immediate: Boolean, returnUrl: String) = {
    val vf = versionFilter(version)
    val disc = openid.discover(identifier)
    val filtered = disc.filter(vf)
    logger.info("(filtered) discovered endpoints:\n%s".format(filtered.map(d =>
      d.asInstanceOf[DiscoveryInformation].getVersion + " : " + d.asInstanceOf[DiscoveryInformation].getOPEndpoint).mkString("\n")))

//    val filtered = disc.filter(!_.asInstanceOf[DiscoveryInformation].isVersion2)
    openid.setImmediateAuth(immediate)
    openid.authenticate(filtered, returnUrl)
  }

  private def versionFilter(version: String): Any => Boolean = version match {
    case "v1" => ! _.asInstanceOf[DiscoveryInformation].isVersion2
    case "v2" => _.asInstanceOf[DiscoveryInformation].isVersion2
    case _ => a: Any => true

  }

  def openidVerify(request: HttpServletRequest) = {
    try {
      val verif = openid.verify(receivingUrl(request), new ParameterList(request.getParameterMap), null )
      List(
        VERIFIED_ID -> (if (verif.getVerifiedId != null) verif.getVerifiedId.getIdentifier else "[no verified identifier]"),
        EXTENSIONS -> extensionsAsStrings(verif.getAuthResponse),
        VERIFIED_MSG -> (if (verif.getStatusMsg != null) verif.getStatusMsg else ""),
        AUTH_RESPONSE -> kvString(verif.getAuthResponse.getParameterMap.map(kv => kv._1.toString -> kv._2.toString).toMap)
      ).toList
    } catch {
      case e: Exception =>
        List(
          VERIFIED_ID -> "[openid response processing unsuccessful]",
          EXTENSIONS -> Nil,
          VERIFIED_MSG -> (e.getMessage + "\n\n" + e.getStackTraceString),
          AUTH_RESPONSE -> kvString(request.getParameterMap.mapValues(_.mkString(" :::: ")).toMap)
        )
    }
  }

}
