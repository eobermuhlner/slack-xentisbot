package ch.obermuhlner.slack.simplebot

fun <T> limitedForLoop(leftSize: Int, rightSize: Int, elements: Collection<T>, block: (T) -> Unit, skipped: (Int) -> Unit): Unit {
	var index = 0
	val n = elements.size
	for (element in elements) {
		if (index < leftSize || index >= n - rightSize) {
			block(element)
		} else {
			if (index == leftSize) {
				skipped(n - leftSize - rightSize)
			}
		}
		index++
	}
}
