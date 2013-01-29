OpenID RP written in Scala, using openid4java library
========================================================================

Build and deployment
------------------------------------------------------------------------

`sbt package`

builds a WAR file, deploy in any application server

Configuration
------------------------------------------------------------------------

    CONFIG_PATH

system property, path for the RP openid request configuration property files

property files may contain additional key-value pairs (usually openid extensions)
to be added to the openid request

special key name: `openid_identifier` is used to populate the ID/URL form field


OpenID login
------------------------------------------------------------------------
    GET /
    GET /login/<config_name>

1) Reads extra request data from the property file located at `CONFIG_PATH/<config_name>.properties`

2) Prompts the use to input or confirm the ID/URL, optionally edit data read from the config file

3) On submit the OpenID authentication flow is initiated

4) OpenID authentication response is collected at /return, and the response data is displayed
