package com.sxtanna.bot;

import com.sxtanna.bot.base.State;
import com.sxtanna.bot.mods.ModuleReactions;
import com.sxtanna.bot.mods.ModuleReference;
import com.sxtanna.bot.mods.base.Module;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GoLangMcBot implements State
{

	public static final long   GID = 684343714180497444L;
	public static final Logger LOG = LoggerFactory.getLogger(GoLangMcBot.class);


	private final List<Module>         modules = new ArrayList<>();
	private final AtomicReference<JDA> discord = new AtomicReference<>();


	@Override
	public void load()
	{
		final var token = getResource("tokens/discord.token");
		if (token.isEmpty())
		{
			return;
		}

		try
		{
			final var discord = new JDABuilder(AccountType.BOT)
					.setToken(token.get())
					.setActivity(Activity.watching("you >:)"))
					.build();
			discord.awaitReady();

			this.discord.set(discord);
		}
		catch (final LoginException ex)
		{
			LOG.error("discord bot authentication failed", ex);
			return;
		}
		catch (final InterruptedException ex)
		{
			LOG.error("discord bot initialization interrupted", ex);
			return;
		}

		LOG.info("discord bot successfully loaded!");

		this.modules.add(new ModuleReactions(this));
		this.modules.add(new ModuleReference(this));

		for (final var module : this.modules)
		{
			try
			{
				module.load();

				LOG.info(String.format("successfully loaded module:%s", module.getName()));
			}
			catch (final Exception ex)
			{
				LOG.error(String.format("failed to load module:%s", module.getName()), ex);
				// todo break and shutdown?
			}
		}
	}

	@Override
	public void kill()
	{
		for (final var module : this.modules)
		{
			try
			{
				module.kill();

				LOG.info(String.format("successfully killed module:%s", module.getName()));
			}
			catch (final Exception ex)
			{
				LOG.error(String.format("failed to kill module:%s", module.getName()), ex);
			}
		}

		this.modules.clear();

		final var discord = this.discord.getAndSet(null);
		if (discord == null)
		{
			return;
		}

		discord.shutdownNow();
	}


	public Optional<JDA> getDiscord()
	{
		return Optional.ofNullable(this.discord.get());
	}

	public Optional<String> getResource(@NotNull final String path)
	{
		try
		{
			final var stream = getClass().getClassLoader().getResourceAsStream(path);
			if (stream == null)
			{
				throw new IllegalStateException("could not find file");
			}

			return Optional.of(new String(stream.readAllBytes()));
		}
		catch (final IOException | IllegalStateException ex)
		{
			LOG.error(String.format("failed to load resource '%s'", path), ex);
		}

		return Optional.empty();
	}

}
