package com.sxtanna.bot;

import com.sxtanna.bot.base.State;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GoLangMcBot implements State
{

	private static final Logger LOG = LoggerFactory.getLogger(GoLangMcBot.class);


	private final AtomicReference<JDA> discord = new AtomicReference<>();


	@Override
	public void load()
	{
		final var token = loadToken();
		if (token.isEmpty())
		{
			return;
		}

		try
		{
			final var discord = new JDABuilder(AccountType.BOT).setToken(token.get()).build();
			discord.awaitReady();

			this.discord.set(discord);
		}
		catch (LoginException ex)
		{
			LOG.error("discord bot authentication failed", ex);
			return;
		}
		catch (InterruptedException ex)
		{
			LOG.error("discord bot initialization interrupted", ex);
			return;
		}

		LOG.info("discord bot successfully loaded!");


	}

	@Override
	public void kill()
	{
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


	private Optional<String> loadToken()
	{
		try
		{
			final var stream = getClass().getClassLoader().getResourceAsStream("discord.token");
			if (stream == null)
			{
				throw new IllegalStateException("could not find file");
			}

			return Optional.of(new String(stream.readAllBytes()));
		}
		catch (IOException | IllegalStateException ex)
		{
			LOG.error("failed to load token from resource 'discord.token'", ex);
		}

		return Optional.empty();
	}

}
