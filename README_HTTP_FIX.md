# HTTP RSS feed support

This fork permits cleartext HTTP connections because RSS feeds may be hosted on
LAN/NAS devices or other endpoints that do not provide HTTPS.

The setting is implemented through `app/src/main/res/xml/network_security_config.xml`
with `cleartextTrafficPermitted="true"` in the base configuration. The app also
retains its existing `android:usesCleartextTraffic="true"` manifest attribute.

Use HTTPS for feeds whenever the server supports it, especially when the feed
contains sensitive or private information.
