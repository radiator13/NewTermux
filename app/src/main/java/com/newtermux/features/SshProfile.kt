package com.newtermux.features

import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class SshProfile {
    @JvmField var id: String = UUID.randomUUID().toString()
    @JvmField var nickname: String = ""
    @JvmField var host: String = ""
    @JvmField var port: Int = 22
    @JvmField var username: String = ""
    @JvmField var keyPath: String = "" // empty = password auth (user types it)

    // Port forwarding / tunnel
    @JvmField var tunnelEnabled: Boolean = false
    @JvmField var tunnelType: String = "local"       // "local" (-L) or "remote" (-R)
    @JvmField var tunnelLocalPort: Int = 8080
    @JvmField var tunnelRemoteHost: String = "localhost"
    @JvmField var tunnelRemotePort: Int = 8080

    fun buildCommand(): String {
        val cmd = StringBuilder("ssh")
        cmd.append(" -o StrictHostKeyChecking=accept-new")
        if (tunnelEnabled && tunnelRemoteHost.isNotEmpty()) {
            val flag = if (tunnelType == "remote") "-R" else "-L"
            cmd.append(" ").append(flag).append(" ")
                .append(tunnelLocalPort).append(":").append(tunnelRemoteHost)
                .append(":").append(tunnelRemotePort)
        }
        if (port != 22) cmd.append(" -p ").append(port)
        if (keyPath.isNotEmpty()) cmd.append(" -i ").append(keyPath)
        cmd.append(" ").append(username).append("@").append(host)
        return cmd.toString()
    }

    fun displayLabel(): String {
        val base = "$username@$host"
        return if (port != 22) "$base:$port" else base
    }

    fun tunnelLabel(): String? {
        if (!tunnelEnabled) return null
        val arrow = if (tunnelType == "remote") "R" else "L"
        return "-$arrow $tunnelLocalPort:$tunnelRemoteHost:$tunnelRemotePort"
    }

    @Throws(JSONException::class)
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("nickname", nickname)
        o.put("host", host)
        o.put("port", port)
        o.put("username", username)
        o.put("keyPath", keyPath)
        o.put("tunnelEnabled", tunnelEnabled)
        o.put("tunnelType", tunnelType)
        o.put("tunnelLocalPort", tunnelLocalPort)
        o.put("tunnelRemoteHost", tunnelRemoteHost)
        o.put("tunnelRemotePort", tunnelRemotePort)
        return o
    }

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(o: JSONObject): SshProfile {
            val p = SshProfile()
            p.id = o.optString("id", UUID.randomUUID().toString())
            p.nickname = o.optString("nickname", "")
            p.host = o.optString("host", "")
            p.port = o.optInt("port", 22)
            p.username = o.optString("username", "")
            p.keyPath = o.optString("keyPath", "")
            p.tunnelEnabled = o.optBoolean("tunnelEnabled", false)
            p.tunnelType = o.optString("tunnelType", "local")
            p.tunnelLocalPort = o.optInt("tunnelLocalPort", 8080)
            p.tunnelRemoteHost = o.optString("tunnelRemoteHost", "localhost")
            p.tunnelRemotePort = o.optInt("tunnelRemotePort", 8080)
            return p
        }
    }
}
