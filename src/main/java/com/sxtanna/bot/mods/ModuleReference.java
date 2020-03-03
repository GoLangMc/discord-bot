package com.sxtanna.bot.mods;

import com.sxtanna.bot.GoLangMcBot;
import com.sxtanna.bot.mods.base.Module;
import io.aexp.nodes.graphql.Argument;
import io.aexp.nodes.graphql.Arguments;
import io.aexp.nodes.graphql.GraphQLRequestEntity;
import io.aexp.nodes.graphql.GraphQLRequestEntity.RequestBuilder;
import io.aexp.nodes.graphql.GraphQLTemplate;
import io.aexp.nodes.graphql.annotations.GraphQLArgument;
import io.aexp.nodes.graphql.annotations.GraphQLArguments;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class ModuleReference extends Module
{

	private static final String  GITHUB_API    = "https://api.github.com/graphql";
	private static final Pattern ISSUE_PATTERN = Pattern.compile("(?<Name>\\w+([-]\\w+)?)+#(?<Numb>\\d+)");


	private final Map<String, GitHubGraphQLRequest> requests = new HashMap<>();
	private final AtomicReference<ListenerAdapter>  listener = new AtomicReference<>();
	private final AtomicReference<ExecutorService>  executor = new AtomicReference<>();


	public ModuleReference(final @NotNull GoLangMcBot bot)
	{
		super(bot);
	}


	@Override
	public @NotNull String getName()
	{
		return "reference";
	}


	@Override
	public void load()
	{
		final var discord = bot.getDiscord().orElseThrow();
		final var graphql = bot.getResource("tokens/graphql.token").orElseThrow();

		final var requests = buildRequests(graphql);
		this.requests.putAll(requests);

		final var executor = Executors.newCachedThreadPool();
		this.executor.set(executor);

		final var listener = new ReferenceListener();
		this.listener.set(listener);

		discord.addEventListener(listener);
	}

	@Override
	public void kill()
	{
		this.requests.clear();

		final var executor = this.executor.getAndSet(null);
		if (executor != null)
		{
			executor.shutdownNow();
		}

		final var listener = this.listener.getAndSet(null);
		if (listener != null)
		{
			final var discord = bot.getDiscord().orElseThrow();
			discord.removeEventListener(listener);
		}
	}


	@NotNull
	@Unmodifiable
	private Map<String, GitHubGraphQLRequest> buildRequests(@NotNull final String graphqlToken)
	{
		final var getIssue = new GitHubGraphQLRequest()
		{
			@Override
			public Optional<RequestBuilder> prepare(@NotNull final Map<String, String> data) throws Exception
			{
				final var name = Optional.ofNullable(data.get("name"));
				final var numb = Optional.ofNullable(data.get("numb")).flatMap(ModuleReference::stringToInt);

				if (name.isEmpty() || numb.isEmpty())
				{
					return Optional.empty();
				}

				final var builder = GraphQLRequestEntity.Builder()
														.url(GITHUB_API)
														.headers(Map.of("Authorization", "bearer " + graphqlToken));

				final var nameArg = new Arguments("repository",
												  new Argument("name", name.get()));
				final var numbArg = new Arguments("repository.issue",
												  new Argument("number", numb.get()));

				return Optional.of(builder.arguments(nameArg, numbArg));
			}
		};

		return Map.of("issues", getIssue);
	}


	private static Optional<Integer> stringToInt(@NotNull final String string)
	{
		try
		{
			return Optional.of(Integer.parseInt(string));
		}
		catch (final NumberFormatException ex)
		{
			return Optional.empty();
		}
	}


	private final class ReferenceListener extends ListenerAdapter
	{

		@Override
		public void onGuildMessageReceived(@Nonnull final GuildMessageReceivedEvent event)
		{
			final var executor = ModuleReference.this.executor.get();
			if (executor == null)
			{
				return;
			}

			if (event.isWebhookMessage())
			{
				return; // don't react to webhook messages
			}

			final var member = event.getMember();
			if (member == null || member.getUser().isBot())
			{
				return;
			}

			final var message = event.getMessage().getContentRaw();

			final var issueMatcher = ISSUE_PATTERN.matcher(message);
			while (issueMatcher.find())
			{
				final var issues = requests.get("issues");
				if (issues == null)
				{
					break;
				}

				final var name = issueMatcher.group("Name");
				final var numb = issueMatcher.group("Numb");

				executor.execute(() ->
								 {
									 final var result = issues.request(GetIssue.class, Map.of("name", name, "numb", numb)).orElse(null);
									 if (result == null)
									 {
										 return;
									 }

									 final var embed = new EmbedBuilder();

									 embed.setColor(result.repository.issue.closed ? Color.RED : Color.GREEN);
									 embed.setTitle(String.format("Issue #%d: %s", result.repository.issue.number, result.repository.issue.title), result.repository.issue.url);

									 embed.setAuthor(result.repository.nameWithOwner, result.repository.url, result.repository.issue.author.avatarUrl);

									 embed.setTimestamp(result.repository.issue.createdAt);

									 event.getChannel().sendMessage(embed.build()).queue();
								 });
			}


		}

	}


	private static abstract class GitHubGraphQLRequest
	{

		protected abstract Optional<RequestBuilder> prepare(@NotNull final Map<String, String> data) throws Exception;

		public <T> Optional<T> request(@NotNull final Class<T> clazz, @NotNull final Map<String, String> data)
		{
			try
			{
				final var graphql = new GraphQLTemplate();
				final var builder = prepare(data).orElseThrow();

				final var request = builder.request(clazz).build();
				final var results = graphql.query(request, clazz);

				return Optional.ofNullable(results.getResponse());

			}
			catch (Exception e)
			{
				return Optional.empty();
			}
		}

	}


	// I don't want to hear a word about this, not. one. word.
	private static final class GetIssue
	{

		@GraphQLArguments(value = {@GraphQLArgument(name = "name"), @GraphQLArgument(name = "owner", value = "GoLangMc")})
		public Repository repository;


		private static final class Repository
		{

			public String url;
			public String nameWithOwner;

			@GraphQLArguments(value = {@GraphQLArgument(name = "number")})
			public Issue issue;

		}

		private static final class Issue
		{

			public Integer        number;
			public String         url;
			public String         title;
			public Author         author;
			public String         bodyText;
			public OffsetDateTime createdAt;
			public Boolean        closed;

		}

		private static final class Author
		{

			public String login;

			@GraphQLArgument(name = "size", value = "100", type = "Integer")
			public String avatarUrl;

		}

	}

}
