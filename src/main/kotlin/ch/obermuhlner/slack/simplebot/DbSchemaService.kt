package ch.obermuhlner.slack.simplebot

import java.io.Reader

interface DbSchemaService {

    fun parse(schemaReader: Reader)

    fun getTableNames(partialTableName: String): List<String>

    fun getTableName(tableId: Long): String?

    fun getTableId(tableName: String): Long?

    fun getTable(tableName: String): DbTable?

    data class DbTable(
            val name: String,
            val id: Long,
            val alias: String?,
            val codeTabGroup: String?,
            val columns: MutableList<DbColumn> = mutableListOf()) {

        fun toMessage(): String {
            var message = "TABLE $name\n"

            for(column in columns) {
                val sizeText = if (column.size == 0) "" else "[${column.size}]"
                val foreignKeyText = if (column.foreignKey == null) "" else " => ${column.foreignKey}"
                val referencesText = if (column.references.size == 0) "" else " -> ${column.references}"
                message += "    %-30s : %-15s (${column.xentisType})$foreignKeyText$referencesText\n"
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
            var nullable: Boolean = false,
            var foreignKey: String? = null,
            val references: MutableList<String> = mutableListOf())
}