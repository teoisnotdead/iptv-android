package app.iptv.player.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los paneles mezclan números con texto, omiten campos y cambian objetos por `[]`. */
class ModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test fun direccionDelServidor() {
        assertEquals("http://servidor.com:8080", normalizePanelUrl("  servidor.com:8080/ "))
        assertEquals("https://servidor.com", normalizePanelUrl("https://servidor.com/"))
        assertEquals("http://servidor.com:8080", normalizePanelUrl("http://servidor.com:8080/player_api.php?username=a&password=b"))
        assertEquals("http://servidor.com", normalizePanelUrl("HTTP://servidor.com/get.php?username=a&type=m3u"))
        assertEquals("http://servidor.com/ruta", normalizePanelUrl("servidor.com/ruta/"))
        assertEquals("", normalizePanelUrl("   "))
    }

    @Test fun mismaCuenta() {
        val a = Creds("http://servidor.com:8080", "ana", "1")
        assertEquals(true, a.sameAccount(Creds("http://SERVIDOR.com:8080", "ana", "2")))
        assertEquals(false, a.sameAccount(Creds("http://otro.com:8080", "ana", "1")))
        assertEquals("servidor.com", a.host)
    }

    @Test fun cuentaConNumerosComoTexto() {
        val info = json.decodeFromString(
            AuthResponse.serializer(),
            """{"user_info":{"auth":"1","status":"Active","exp_date":1893456000,"max_connections":"3","active_cons":0,"otro":{}},"server_info":{}}""",
        ).userInfo
        assertEquals(1, info.auth)
        assertEquals("1893456000", info.expDate)
        assertEquals("3", info.maxConnections)
        assertEquals("0", info.activeConnections)
        assertNull(accountProblem(info))
    }

    @Test fun cuentaSinVencimientoYEstados() {
        val info = json.decodeFromString(AuthResponse.serializer(), """{"user_info":{"auth":1,"status":"Expired","exp_date":null}}""").userInfo
        assertEquals("", info.expDate)
        assertEquals(LoginError.EXPIRED, accountProblem(info))
        assertEquals(LoginError.DISABLED, accountProblem(UserInfo(auth = 1, status = "Banned")))
        assertEquals(LoginError.INACTIVE, accountProblem(UserInfo(auth = 1, status = "Trial")))
    }

    @Test fun credencialesMalas() {
        assertEquals(0, json.decodeFromString(AuthResponse.serializer(), """{"user_info":{"auth":0}}""").userInfo.auth)
        assertEquals(0, json.decodeFromString(AuthResponse.serializer(), """{"user_info":[]}""").userInfo.auth)
        assertEquals(0, json.decodeFromString(AuthResponse.serializer(), """{}""").userInfo.auth)
    }

    @Test fun listasConCamposMezclados() {
        val live = json.decodeFromString(
            ListSerializer(LiveStream.serializer()),
            """[{"num":"7","name":"Canal 7","stream_id":"1234","stream_icon":null,"category_id":5,"epg_channel_id":null},{"name":"Sin id"}]""",
        )
        assertEquals(1234, live[0].streamId)
        assertEquals(7, live[0].num)
        assertEquals("", live[0].icon)
        assertEquals("5", live[0].categoryId)
        assertEquals(0, live[1].streamId)

        val vod = json.decodeFromString(
            ListSerializer(VodStream.serializer()),
            """[{"stream_id":99,"name":"Película","rating":7.5,"container_extension":"mkv","category_id":"12","added":"1700000000"}]""",
        )
        assertEquals(99, vod[0].streamId)
        assertEquals("7.5", vod[0].rating)
        assertEquals("mkv", vod[0].containerExtension)
    }
}
