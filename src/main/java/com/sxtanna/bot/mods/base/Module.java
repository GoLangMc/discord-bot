package com.sxtanna.bot.mods.base;

import com.sxtanna.bot.GoLangMcBot;
import com.sxtanna.bot.base.Named;
import com.sxtanna.bot.base.State;
import org.jetbrains.annotations.NotNull;

public abstract class Module implements Named, State
{

	@NotNull
	protected final GoLangMcBot bot;


	protected Module(@NotNull final GoLangMcBot bot)
	{
		this.bot = bot;
	}

}
