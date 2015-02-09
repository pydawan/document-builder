package com.craigburke.document.core

class Cell extends BaseNode {
	List children = []
	Integer padding = 10
	Align align = Align.LEFT

	Integer position = 0
	Integer width
}