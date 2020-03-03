package com.sxtanna.bot.mods;

import com.sxtanna.bot.GoLangMcBot;
import com.sxtanna.bot.mods.base.Module;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class ModuleReactions extends Module
{

	private static final String ARROW_U = "U+2B06";
	private static final String ARROW_D = "U+2B07";


	private final AtomicReference<ListenerAdapter> listener = new AtomicReference<>();


	public ModuleReactions(final @NotNull GoLangMcBot bot)
	{
		super(bot);
	}


	@NotNull
	@Override
	public String getName()
	{
		return "reactions";
	}


	@Override
	public void load()
	{
		final var discord = bot.getDiscord().orElseThrow();

		final var guild = discord.getGuildById(GoLangMcBot.GID);
		if (guild == null)
		{
			throw new IllegalStateException("unable to find guild!");
		}

		final var categories = guild.getCategoriesByName("github", true);
		if (categories.isEmpty())
		{
			return; // no need to continue
		}

		final var channels = categories.stream().flatMap(it -> it.getTextChannels().stream()).map(ISnowflake::getIdLong).collect(Collectors.toSet()); // god I miss kotlin ;(
		if (channels.isEmpty())
		{
			return; // no need to continue
		}

		final var listener = new ReactionListener(channels);
		discord.addEventListener(listener);

		this.listener.set(listener);
	}

	@Override
	public void kill()
	{
		final var listener = this.listener.getAndSet(null);
		if (listener == null)
		{
			return;
		}

		final var discord = bot.getDiscord().orElseThrow();

		discord.removeEventListener(listener);
	}


	private static final class ReactionListener extends ListenerAdapter
	{

		@NotNull
		private final Set<Long> channels;


		private ReactionListener(@NotNull final Set<Long> channels)
		{
			this.channels = channels;
		}


		@Override
		public void onGuildMessageReceived(@Nonnull final GuildMessageReceivedEvent event)
		{
			if (!channels.contains(event.getChannel().getIdLong()))
			{
				return; // not a watched channel
			}

			addReactionsToMessage(event.getMessage());
		}

		@Override
		public void onGuildMessageReactionRemoveAll(@Nonnull final GuildMessageReactionRemoveAllEvent event)
		{
			if (!channels.contains(event.getChannel().getIdLong()))
			{
				return; // not a watched channel
			}

			// add back default reactions
			event.getChannel().retrieveMessageById(event.getMessageIdLong()).queue(this::addReactionsToMessage);
		}


		private void addReactionsToMessage(@NotNull final Message message)
		{
			message.addReaction(ARROW_U).queue();
			message.addReaction(ARROW_D).queue();
		}

	}

}
