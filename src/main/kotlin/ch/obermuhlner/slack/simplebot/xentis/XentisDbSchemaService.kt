package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.DbSchemaService
import ch.obermuhlner.slack.simplebot.DbSchemaService.DbTable
import ch.obermuhlner.slack.simplebot.DbSchemaService.DbColumn
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import org.xml.sax.Attributes

class XentisDbSchemaService : DbSchemaService {

	private val tableNameToTable = mutableMapOf<String, DbTable?>()
	private val tableIdToTable = mutableMapOf<Long, DbTable?>()
		
	override fun parse(schemaFile: String) {
		tableNameToTable.clear()
		tableIdToTable.clear()
		
		val factory = SAXParserFactory.newInstance()
		val parser = factory.newSAXParser()
		
		val handler = object: DefaultHandler() {
			var table: DbTable = DbTable("?", 0)
			var column: DbColumn = DbColumn("?")
			var currentElementName: String? = null
			
			override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
				currentElementName = qName
				when (qName) {
					"Table" -> {
						table = DbTable(attributes.getValue("name"), parseLong(attributes.getValue("id")))
						tableNameToTable[table.name] = table
						tableIdToTable[table.id] = table
						
					}
					"Column" -> {
						column = DbColumn(attributes.getValue("name"))
						table.columns.add(column)
					}
					"Format" -> {
						column.oracleType = attributes.getValue("oracle")
						column.xentisType = attributes.getValue("xentis") 
						column.size = parseInt(attributes.getValue("size"))
					}
					"ForeignKey" -> {
					}
				}
			}
			
			override fun characters(chars: CharArray, start: Int, length: Int) {
				val text = String(chars, start, length)
				when(currentElementName) {
					"Reference" -> {
						column.references.add(text)
					}
					"ForeignKey" -> {
						column.foreignKey = text
					}
				}
			}
			
			override fun endElement(uri: String, localName: String, qName: String) {
				currentElementName = null
			}
		}
		
		parser.parse(File(schemaFile), handler)
	}
	
	private fun parseLong(text: String?, defaultValue: Long = 0): Long {
		if (text == null) {
			return defaultValue
		}
		return java.lang.Long.parseLong(text)
	}
	
	private fun parseInt(text: String?, defaultValue: Int = 0): Int {
		if (text == null) {
			return defaultValue
		}
		return java.lang.Integer.parseInt(text)
	}
	
	override fun getTableNames(partialTableName: String): List<String> {
		val uppercasePartialTableName = partialTableName.toUpperCase()
		val results: MutableList<String> = mutableListOf()
		for (tableName in tableNameToTable.keys) {
			if (tableName.contains(uppercasePartialTableName)) {
				results.add(tableName)
			}
		}
		return results
	}
	
	override fun getTableName(tableId: Long): String? {
		return tableIdToTable[tableId]?.name
	}
	
	override fun getTableId(tableName: String): Long? {
		return tableNameToTable[tableName.toUpperCase()]?.id
	}
	
	override fun getTable(tableName: String): DbTable? {
		return tableNameToTable[tableName]
	}
}
