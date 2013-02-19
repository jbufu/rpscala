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

edit in `build.sbt` for usage with `~;container:start; container:reload /` in sbt

property files may contain additional key-value pairs (usually openid extensions)
to be added to the openid request

special key names:

`openid_identifier` is used to populate the ID/URL form field

`openid_version`: "v1" selects and forces only use of OpenID v1, "v2" - only OpenID v2


OpenID login
------------------------------------------------------------------------
    GET /
    GET /login/<config_name>
    GET /login/config_name1/config_name2/...

Example: login with google (identifier_select) ID and request SREG:

    GET /login/idgoog/sreg

1) Reads extra request data from the property file located at `CONFIG_PATH/<config_nameX>.properties`
Multiple configurations can be appended together. See some sample extension properties in the `configs/` folder.

2) Prompts the use to input or confirm the ID/URL, optionally edit data read from the config file

3) On submit the OpenID authentication flow is initiated

4) OpenID authentication response is collected at `/return`, and the response data is displayed
