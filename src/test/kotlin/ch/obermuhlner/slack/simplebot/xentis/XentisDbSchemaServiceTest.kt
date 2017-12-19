package ch.obermuhlner.slack.simplebot.xentis

import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.StringReader

class XentisDbSchemaServiceTest {

    private lateinit var service: XentisDbSchemaService

    @Before
    fun setup() {
        service = XentisDbSchemaService()

        service.parse(StringReader("""
            | <Table name="EXAMPLE" id="0002" type="General" blockSize="10" cachingStrategy="KeepRecent" codeTabGroup="Valor" alias="Beispiel">
            |      <Columns>
            |        <Column name="EXAMPLE_ID" nullable="false">
            |          <Format oracle="RAW" size="8" xentis="id"/>
            |        </Column>
            |        <Column name="IDENTEXT_TXT" nullable="true">
            |          <Format oracle="VARCHAR2" size="50" xentis="string"/>
            |        </Column>
            |        <Column name="INSTITUT_ID" nullable="true">
            |          <Format oracle="RAW" size="8" xentis="id">
            |            <ForeignKey name="">INSTITUT</ForeignKey>
            |          </Format>
            |        </Column>
            |        <Column name="INAKTIV_DAT" nullable="true">
            |          <Format oracle="DATE" xentis="date"/>
            |        </Column>
            |        <Column name="SYSVERKNART_BIT" nullable="false">
            |          <Format oracle="RAW" size="8" xentis="bitset" bits="VerknartBits"/>
            |        </Column>
            |      </Columns>
            |    </Table>
            """.trimMargin()))
    }

    @Test
    fun test_getTableId() {
        val id = service.getTableId("EXAMPLE")
        assertEquals(2L, id)
    }

    @Test
    fun test_getTableName() {
        val name = service.getTableName(2L)
        assertEquals("EXAMPLE", name)
    }

    @Test
    fun test_getTableNames() {
        val names = service.getTableNames("EXAM")
        assertEquals(listOf("EXAMPLE"), names)
    }

    @Test
    fun test_getTable_UNKNOWN() {
        val tableNull = service.getTable("UNKNOWN")
        assertEquals(null, tableNull)
    }

    @Test
    fun test_getTable_EXAMPLE() {
        val tableExample = service.getTable("EXAMPLE")
        assertEquals(2L, tableExample!!.id)
        assertEquals("EXAMPLE", tableExample.name)
        assertEquals(5, tableExample.columns.size)
        assertEquals("Beispiel", tableExample.alias)
        assertEquals("Valor", tableExample.codeTabGroup)
    }

    @Test
    fun test_getTable_EXAMPLE_column_exampleId() {
        val tableExample = service.getTable("EXAMPLE")!!

        val columnExampleId = tableExample.columns[0]
        assertEquals("EXAMPLE_ID", columnExampleId.name)
        assertEquals("RAW", columnExampleId.oracleType)
        assertEquals(8, columnExampleId.size)
        assertEquals("id", columnExampleId.xentisType)
        assertEquals(false, columnExampleId.nullable)
    }

    @Test
    fun test_getTable_EXAMPLE_column_identTxt() {
        val tableExample = service.getTable("EXAMPLE")!!

        val columnIdentTxt = tableExample.columns[1]
        assertEquals("IDENTEXT_TXT", columnIdentTxt.name)
        assertEquals("VARCHAR2", columnIdentTxt.oracleType)
        assertEquals(50, columnIdentTxt.size)
        assertEquals("string", columnIdentTxt.xentisType)
        assertEquals(true, columnIdentTxt.nullable)
    }

    @Test
    fun test_getTable_EXAMPLE_column_institutId() {
        val tableExample = service.getTable("EXAMPLE")!!

        val columnInstitutId = tableExample.columns[2]
        assertEquals("INSTITUT_ID", columnInstitutId.name)
        assertEquals("RAW", columnInstitutId.oracleType)
        assertEquals(8, columnInstitutId.size)
        assertEquals("id", columnInstitutId.xentisType)
        assertEquals("INSTITUT", columnInstitutId.foreignKey)
        assertEquals(true, columnInstitutId.nullable)
    }

    @Test
    fun test_getTable_EXAMPLE_column_inaktivDat() {
        val tableExample = service.getTable("EXAMPLE")!!

        val columnInaktivDat = tableExample.columns[3]
        assertEquals("INAKTIV_DAT", columnInaktivDat.name)
        assertEquals("DATE", columnInaktivDat.oracleType)
        assertEquals(0, columnInaktivDat.size)
        assertEquals("date", columnInaktivDat.xentisType)
        assertEquals(true, columnInaktivDat.nullable)
    }

    @Test
    fun test_getTable_EXAMPLE_column_verknartBits() {
        val tableExample = service.getTable("EXAMPLE")!!

        val columnVerknartBits = tableExample.columns[4]
        assertEquals("SYSVERKNART_BIT", columnVerknartBits.name)
        assertEquals("RAW", columnVerknartBits.oracleType)
        assertEquals(8, columnVerknartBits.size)
        assertEquals("bitset", columnVerknartBits.xentisType)
        assertEquals(false, columnVerknartBits.nullable)
    }

    @Test
    fun test_getTable_EXAMPLE_toMessage() {
        val tableExample = service.getTable("EXAMPLE")!!

        val expected = """
            |TABLE EXAMPLE ALIAS Beispiel CODETABGROUP Valor
            |    EXAMPLE_ID                     : RAW[8]          (id)
            |    IDENTEXT_TXT                   : VARCHAR2[50]    (string)
            |    INSTITUT_ID                    : RAW[8]          (id) => INSTITUT
            |    INAKTIV_DAT                    : DATE            (date)
            |    SYSVERKNART_BIT                : RAW[8]          (bitset)
            |
            """.trimMargin()
        val actual = tableExample.toMessage()
        assertEquals(expected, actual)
    }
}
