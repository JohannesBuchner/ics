package com.jakeapp.jake.ics.impl.ice.filetransfer.fsm;


public abstract class BlockEventListener<T> {

	public void onEnter(Block<T> block, T e) {
	};

	public void onEmpty(Block<T> block) {
	};

}
