package com.jakeapp.jake.ics.impl.ice.filetransfer.fsm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


public class Block<T> {

	private Set<BlockEventListener<T>> listeners = new HashSet<BlockEventListener<T>>();

	public void registerListener(BlockEventListener<T> l) {
		listeners.add(l);
	}

	public void unregisterListener(BlockEventListener<T> l) {
		listeners.remove(l);
	}

	private Queue<T> content = new ConcurrentLinkedQueue<T>();

	public void enter(T e) {
		content.add(e);
		for (BlockEventListener<T> l : listeners) {
			l.onEnter(this, e);
		}
	}

	public void leave(T e) {
		content.remove(e);
		triggerOnEmpty();
	}
	public void triggerOnEmpty() {
		if (content.isEmpty()) {
			for (BlockEventListener<T> l : listeners) {
				l.onEmpty(this);
			}
		}
	}
	
	public Queue<T> getContent() {
		return content;
	}

}
