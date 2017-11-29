package ch.obermuhlner.slack.simplebot

import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import org.xml.sax.Attributes

class XentisDbSchema {

	val tableIdToTableName = mutableMapOf<Long, String?>()
	val tableNameToTableId = mutableMapOf<String, Long?>()
		
	fun parse(schemaFile: String) {
		val factory = SAXParserFactory.newInstance()
		val parser = factory.newSAXParser()
		
		val handler = object: DefaultHandler() {
			var tableName = ""
			var tableId = 0L
			var columnName = ""
			var formatOracle = ""
			var formatXentis = ""
			var formatSize = 0
			var foreignKeyName = ""
			
			override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
				when (qName) {
					"Table" -> {
						tableName = attributes.getValue("name")
						tableId = java.lang.Long.parseLong(attributes.getValue("id"))
						
						tableIdToTableName[tableId] = tableName
						tableNameToTableId[tableName] = tableId
						// TODO clear list of columns
					}
					"Column" -> {
						columnName = attributes.getValue("name")
					}
					"Format" -> {
						formatOracle = attributes.getValue("oracle")
						formatXentis = attributes.getValue("xentis")
						//formatSize = java.lang.Integer.parseInt(attributes.getValue("xentis", "0"))
					}
					"ForeignKey" -> {
						foreignKeyName = attributes.getValue("name")
					}
				}
			}
			
			override fun endElement(uri: String, localName: String, qName: String) {
				when (qName) {
					"Table" -> {
						// TODO parse end of table
					}
				}
			}
		}
		
		parser.parse(File(schemaFile), handler)
	}
	
	fun getTableName(tableId: Long): String? {
		return tableIdToTableName[tableId]
	}
	
	fun getTableId(tableName: String): Long? {
		return tableNameToTableId[tableName.toUpperCase()]
	}
}