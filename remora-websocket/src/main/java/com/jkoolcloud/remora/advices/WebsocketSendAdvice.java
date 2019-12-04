package com.jkoolcloud.remora.advices;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.remora.RemoraConfig;
import com.jkoolcloud.remora.core.CallStack;
import com.jkoolcloud.remora.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class WebsocketSendAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "WebsocketSendAdvice";
	public static String[] INTERCEPTING_CLASS = { "javax.websocket.RemoteEndpoint" };
	public static String INTERCEPTING_METHOD = "send";

	@RemoraConfig.Configurable
	public static boolean load = true;
	@RemoraConfig.Configurable
	public static boolean logging = false;
	public static TaggedLogger logger;

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return nameStartsWith("send").and(takesArgument(0, String.class));
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return hasSuperType(nameStartsWith(INTERCEPTING_CLASS[0])).and(not(isInterface()));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(WebsocketSendAdvice.class.getClassLoader()).include(RemoraConfig.INSTANCE.classLoader)//
			.advice(methodMatcher(), WebsocketSendAdvice.class.getName());

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
	 * @param arguments
	 *            arguments provided for method
	 * @param method
	 *            instrumented method description
	 * @param ed
	 *            {@link EntryDefinition} for collecting ant passing values to
	 *            {@link com.jkoolcloud.remora.core.output.OutputManager}
	 * @param startTime
	 *            method startTime
	 *
	 */

	@Advice.OnMethodEnter
	public static void before(@Advice.This RemoteEndpoint thiz, //
			@Advice.AllArguments Object[] arguments, //
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, //
			@Advice.Local("startTime") long startTime) {
		try {
			if (ed == null) {
				ed = new EntryDefinition(WebsocketSendAdvice.class);
			}
			if (logging) {
				logger.info(format("Entering: {0} {1}", WebsocketSendAdvice.class.getName(), "before"));
			}
			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, logging ? logger : null);
			ed.setEventType(EntryDefinition.EventType.SEND);
			Session session = WebsocketSessionAdvice.sessionEndpoints.get(thiz);
			if (session == null) {
				session = WebsocketSessionAdvice.sessionHandlers.get(thiz);
			}
			if (session != null) {
				String correlator = session.getId();
				URI requestURI = session.getRequestURI();

				ed.setResource(requestURI.toASCIIString(), EntryDefinition.ResourceType.NETADDR);
				ed.setCorrelator(correlator);
				ed.addPropertyIfExist("SESSION", correlator);
				CallStack<EntryDefinition> stack = (CallStack) stackThreadLocal.get();
				String server = null;
				try {
					server = session.getUserProperties().get("javax.websocket.endpoint.remoteAddress").toString();
					stack.setServer(server);
				} catch (NullPointerException e) {

				}

				Pattern compile = Pattern.compile("\\/.[^/]*\\/");
				Matcher matcher = compile.matcher(requestURI.toASCIIString());
				String application = null;
				if (matcher.find()) {
					application = matcher.group(0);
					stack.setApplication(application);
				}

				if (logging) {
					logger.info("Attached correlator {0}, server {1}, application {2}", correlator, server,
							application);
				}
			} else {
				logger.info("No session found");
			}
		} catch (Throwable t) {
			handleAdviceException(t, ADVICE_NAME, logging ? logger : null);
		}
	}

	/**
	 * Method called on instrumented method finished.
	 *
	 * @param obj
	 *            reference to method object
	 * @param method
	 *            instrumented method description
	 * @param arguments
	 *            arguments provided for method
	 * @param exception
	 *            exception thrown in method exit (not caught)
	 * @param ed
	 *            {@link EntryDefinition} passed along the method (from before method)
	 * @param startTime
	 *            startTime passed along the method
	 */

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void after(@Advice.This Object obj, //
			@Advice.Origin Method method, //
			@Advice.AllArguments Object[] arguments, //
			// @Advice.Return Object returnValue, // //TODO needs separate Advice capture for void type
			@Advice.Thrown Throwable exception, @Advice.Local("ed") EntryDefinition ed, //
			@Advice.Local("startTime") long startTime) {
		boolean doFinally = true;
		try {
			if (ed == null) { // ed expected to be null if not created by entry, that's for duplicates
				if (logging) {
					logger.info("EntryDefinition not exist, entry might be filtered out as duplicate or ran on test");
				}
				doFinally = false;
				return;
			}
			if (logging) {
				logger.info(format("Exiting: {0} {1}", WebsocketSendAdvice.class.getName(), "after"));
			}
			fillDefaultValuesAfter(ed, startTime, exception, logging ? logger : null);
		} catch (Throwable t) {
			handleAdviceException(t, ADVICE_NAME, logging ? logger : null);
		} finally {
			if (doFinally) {
				doFinally();
			}
		}

	}

	@Override
	protected AgentBuilder.Listener getListener() {
		return new TransformationLoggingListener(logger);
	}

	@Override
	public void install(Instrumentation instrumentation) {
		logger = Logger.tag(ADVICE_NAME);
		if (load) {
			getTransform().with(getListener()).installOn(instrumentation);
		} else {
			logger.info("Advice {0} not enabled", getName());
		}
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

}