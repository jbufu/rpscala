package com.janrain.rpscala

import org.slf4j.LoggerFactory


/**
 * @author Johnny Bufu
 */
trait Loggable { self =>

  val logger = LoggerFactory.getLogger(self.getClass)

  def logDebug(msg: => String) {
    if (logger.isDebugEnabled) logger.debug(msg)
  }
}
