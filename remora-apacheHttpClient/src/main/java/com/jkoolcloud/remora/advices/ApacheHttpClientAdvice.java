package com.jkoolcloud.remora.advices;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.conn.routing.HttpRoute;
import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.remora.RemoraConfig;
import com.jkoolcloud.remora.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ApacheHttpClientAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "ApacheHttpClientAdvice";
	public static String[] INTERCEPTING_CLASS = { "<CHANGE HERE>" };
	public static String INTERCEPTING_METHOD = "<CHANGE HERE>";

	@RemoraConfig.Configurable
	public static String headerCorrIDName = "REMORA_CORR";
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
		return named("execute").and(takesArguments(4))
				.and(returns(hasSuperType(named("org.apache.http.client.methods.CloseableHttpResponse"))))
				.and(takesArgument(0, hasSuperType(named("org.apache.http.conn.routing.HttpRoute"))))
				.and(takesArgument(1, hasSuperType(named("org.apache.http.client.methods.HttpRequestWrapper"))))
				.and(takesArgument(2, hasSuperType(named("org.apache.http.client.protocol.HttpClientContext"))))
				.and(takesArgument(3, hasSuperType(named("org.apache.http.client.methods.HttpExecutionAware"))));
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return hasSuperType(named("org.apache.http.impl.execchain.ClientExecChain"));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(ApacheHttpClientAdvice.class.getClassLoader())//
			.include(RemoraConfig.INSTANCE.classLoader) //
			.advice(methodMatcher(), ApacheHttpClientAdvice.class.getName());

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
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
	public static void before(@Advice.This Object thiz, //
			@Advice.Argument(0) HttpRoute route, @Advice.Argument(1) HttpRequestWrapper request,
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, //
			@Advice.Local("startTime") long startTime) {
		try {
			if (isChainedClassInterception(ApacheHttpClientAdvice.class, logging ? logger : null)) {
				return; // return if its chain of same
			}
			if (ed == null) {
				ed = new EntryDefinition(ApacheHttpClientAdvice.class);
			}
			if (logging) {
				logger.info(format("Entering: {0} {1}", ApacheHttpClientAdvice.class.getName(), "before"));
			}
			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, logging ? logger : null);
			ed.addPropertyIfExist("URI", request.getURI().toString());
			ed.addPropertyIfExist("HOST", route.getTargetHost().getHostName());
			ed.setResource(request.getURI().toString(), EntryDefinition.ResourceType.NETADDR);

			request.addHeader(headerCorrIDName, ed.getId());
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
				logger.info(format("Exiting: {0} {1}", ApacheHttpClientAdvice.class.getName(), "after"));
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
	public void install(Instrumentation inst) {
		logger = Logger.tag(ADVICE_NAME);
		getTransform().with(getListener()).installOn(inst);
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}
}