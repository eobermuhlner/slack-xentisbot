package ch.obermuhlner.slack.simplebot

import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import org.xml.sax.Attributes

class XentisDbSchema {

	val tableNameToTable = mutableMapOf<String, DbTable?>()
	val tableIdToTable = mutableMapOf<Long, DbTable?>()
		
	fun parse(schemaFile: String) {
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
	
	fun parseLong(text: String?, defaultValue: Long = 0): Long {
		if (text == null) {
			return defaultValue
		}
		return java.lang.Long.parseLong(text)
	}
	
	fun parseInt(text: String?, defaultValue: Int = 0): Int {
		if (text == null) {
			return defaultValue
		}
		return java.lang.Integer.parseInt(text)
	}
	
	fun getTableNames(partialTableName: String): List<String> {
		val uppercasePartialTableName = partialTableName.toUpperCase()
		val results: MutableList<String> = mutableListOf()
		for (tableName in tableNameToTable.keys) {
			if (tableName.contains(uppercasePartialTableName)) {
				results.add(tableName)
			}
		}
		return results
	}
	
	fun getTableName(tableId: Long): String? {
		return tableIdToTable[tableId]?.name
	}
	
	fun getTableId(tableName: String): Long? {
		return tableNameToTable[tableName.toUpperCase()]?.id
	}
	
	fun getTable(tableName: String): DbTable? {
		return tableNameToTable[tableName]
	}
}

data class DbTable(
		val name: String,
		val id: Long,
		val columns: MutableList<DbColumn> = mutableListOf()) {
	fun toMessage(): String {
		var message = "TABLE $name\n"
		
		for(column in columns) {
			val sizeText = if (column.size == 0) "" else "[${column.size}]"
			val foreignKeyText = if (column.foreignKey == null) "" else " => ${column.foreignKey}"
			val referencesText = if (column.references.size == 0) "" else " -> ${column.references}"
			message += "    %-30s : %-10s (${column.xentisType})$foreignKeyText$referencesText\n"
					.format(column.name, "${column.oracleType}$sizeText")
		}
		
		return message
	}
}

data class DbColumn(
		val name: String,
		var oracleType: String = "",
		var xentisType: String = "",
		var size: Int = 0,
		var foreignKey: String? = null,
		val references: MutableList<String> = mutableListOf())

