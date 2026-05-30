package com.termux.shared.termux.data

import java.util.LinkedHashSet
import java.util.regex.Pattern

object TermuxUrlUtils {

    @JvmField
    var URL_MATCH_REGEX: Pattern? = null

    @JvmStatic
    fun getUrlMatchRegex(): Pattern {
        URL_MATCH_REGEX?.let { return it }

        val regex = buildString {
            append("(")                       // Begin first matching group.
            append("(?:")                     // Begin scheme group.
            append("dav|")                    // The DAV proto.
            append("dict|")                   // The DICT proto.
            append("dns|")                    // The DNS proto.
            append("file|")                   // File path.
            append("finger|")                 // The Finger proto.
            append("ftp(?:s?)|")              // The FTP proto.
            append("git|")                    // The Git proto.
            append("gemini|")                 // The Gemini proto.
            append("gopher|")                 // The Gopher proto.
            append("http(?:s?)|")             // The HTTP proto.
            append("imap(?:s?)|")             // The IMAP proto.
            append("irc(?:[6s]?)|")           // The IRC proto.
            append("ip[fn]s|")                // The IPFS proto.
            append("ldap(?:s?)|")             // The LDAP proto.
            append("pop3(?:s?)|")             // The POP3 proto.
            append("redis(?:s?)|")            // The Redis proto.
            append("rsync|")                  // The Rsync proto.
            append("rtsp(?:[su]?)|")          // The RTSP proto.
            append("sftp|")                   // The SFTP proto.
            append("smb(?:s?)|")              // The SAMBA proto.
            append("smtp(?:s?)|")             // The SMTP proto.
            append("""svn(?:(?:\+ssh)?)|""")   // The Subversion proto.
            append("tcp|")                    // The TCP proto.
            append("telnet|")                 // The Telnet proto.
            append("tftp|")                   // The TFTP proto.
            append("udp|")                    // The UDP proto.
            append("vnc|")                    // The VNC proto.
            append("ws(?:s?)")                // The Websocket proto.
            append(")://")                    // End scheme group.
            append(")")                       // End first matching group.

            // Begin second matching group.
            append("(")
            // User name and/or password in format 'user:pass@'.
            append("""(?:\S+(?::\S*)?@)?""")
            // Begin host group.
            append("(?:")
            // IP address.
            append("""(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|""")
            // Host name or domain.
            append("""(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)(?:(?:\.(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff0-9]-*){1,}[a-z\u00a1-\uffff0-9]{1,}))?|""")
            // Just path for file:// scheme.
            append("""/(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)""")
            // End host group.
            append(")")
            // Port number.
            append("""(?::\d{1,5})?""")
            // Resource path with optional query string.
            append("""(?:/[a-zA-Z0-9:@%\-._~!$&()*+,;=?/]*)?""")
            // Fragment.
            append("""(?:#[a-zA-Z0-9:@%\-._~!$&()*+,;=?/]*)?""")
            // End second matching group.
            append(")")
        }

        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL)
        URL_MATCH_REGEX = pattern
        return pattern
    }

    @JvmStatic
    fun extractUrls(text: String): LinkedHashSet<CharSequence> {
        val urlSet = LinkedHashSet<CharSequence>()
        val matcher = getUrlMatchRegex().matcher(text)
        while (matcher.find()) {
            val url = text.substring(matcher.start(1), matcher.end())
            urlSet.add(url)
        }
        return urlSet
    }
}
