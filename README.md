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

`openid_version`: "v1" selects and forces only use of OpenID v1, "v2" - only OpenID v2; default (if this entry is not specified) is to prefer v2 and fallback to v1

`openid_immediate`: if "true" sends checkid_immediate OpenID requests (instead of checkid_setup); defaults to false

`openid_redirect`: if "true" sends the OpenID request via a GET redirect (instead of POST) to the OP endpoint URL


OpenID login
------------------------------------------------------------------------
    GET /
    GET /login/<config_name>
    GET /login/config_name1/config_name2/...

Example: login with google (identifier_select) ID and request SREG:

    GET /login/idgoog/sreg

1) Reads extra request data from the property file located at `CONFIG_PATH/<config_nameX>.properties`
Multiple configurations can be appended together. See some sample extension properties in the `configs/` folder.

2) Prompts the user to input or confirm the ID/URL, optionally edit data that was read from the config file

3) On submit the OpenID authentication flow is initiated

4) OpenID authentication response is collected at `/return`, and the response data is displayed


Janrain Engage login
------------------------------------------------------------------------

Provision a Janrain Engage account, and configure it in `CONFIG_PATH/<engage_app_name>.properties` :

    engage_api_key=<secret>

Login at:

    GET /engage/<engage_app_name>

Token URL where Janrain Engage will post the token after IdP authentication is:

    POST /engage/<engage_app_name>

