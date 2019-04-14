package ch.njol.tome.eclipse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.tome.Constants;
import ch.njol.tome.common.ModuleIdentifier;
import ch.njol.tome.interpreter.InterpretedNormalObject;
import ch.njol.tome.interpreter.InterpretedObject;
import ch.njol.tome.interpreter.nativetypes.InterpretedNativeSystem;
import ch.njol.tome.ir.definitions.IRAttributeRedefinition;
import ch.njol.tome.ir.definitions.IRBrokkrClassDefinition;
import ch.njol.tome.ir.definitions.IRParameterDefinition;
import ch.njol.tome.ir.definitions.IRParameterRedefinition;
import ch.njol.tome.ir.definitions.IRTypeDefinition;
import ch.njol.tome.moduleast.ASTModule;

public final class InterpreterProcess implements IProcess {
	
	private final ILaunch launch;
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private final Thread thread;
	
	private final Map<String, String> attributes = new HashMap<>();
	
	private final @Nullable IProgressMonitor monitor;
	
	private final ILaunchConfiguration configuration;
	
	public InterpreterProcess(ILaunchConfiguration configuration, String mode, ILaunch launch, @Nullable IProgressMonitor monitor) {
		// TODO param: start class/method/...
		
		this.configuration = configuration;
		this.launch = launch;
		this.monitor = monitor;
		
		thread = new Thread(this::run);
		thread.start();
	}
	
	@SuppressWarnings("null")
	private void run() {
		try {
			
			String mainClassPath = configuration.getAttribute(LaunchConfigurationDelegate.KEY_MAIN_CLASS, "");
			int lastDot = mainClassPath.lastIndexOf('.');
			String modulePath = mainClassPath.substring(0, lastDot);
			String mainClass = mainClassPath.substring(lastDot + 1);
			
			System.out.println("Running with attributes=" + attributes + "; mainClass=" + mainClass);
			
			ASTModule module = Plugin.getModule(new ModuleIdentifier(modulePath));
			
			IRTypeDefinition mainType = module.getDeclaredType(mainClass);
			
			IRAttributeRedefinition mainMethod = mainType.getAttributeByName("main");
			Map<IRParameterDefinition, InterpretedObject> arguments = new HashMap<>();
			for (IRParameterRedefinition param : mainMethod.parameters()) {
				arguments.put(param.definition(), new InterpretedNativeSystem(param.getIRContext()));
			}
			mainMethod.interpretDispatched(new InterpretedNormalObject((IRBrokkrClassDefinition) mainType), arguments, false);
		} catch (CoreException e) {
			System.err.println("Internal Eclipse error: " + e);
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void terminate() throws DebugException {
		running.set(false);
		thread.interrupt();
	}
	
	@Override
	public boolean isTerminated() {
		return !thread.isAlive();
	}
	
	@Override
	public boolean canTerminate() {
		return true;
	}
	
	@Override
	public <T> @Nullable T getAdapter(final Class<T> adapter) {
		return null;
	}
	
	@Override
	public void setAttribute(final String key, @Nullable final String value) {
		if (value == null)
			attributes.remove(key);
		else
			attributes.put(key, value);
	}
	
	@Override
	public @Nullable String getAttribute(final String key) {
		return attributes.get(key);
	}
	
	private static final class StreamsProxy implements IStreamsProxy {
		@Override
		public @Nullable IStreamMonitor getErrorStreamMonitor() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public @Nullable IStreamMonitor getOutputStreamMonitor() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public void write(final String input) throws IOException {
			// TODO Auto-generated method stub
			
		}
	}
	
	@Override
	public IStreamsProxy getStreamsProxy() {
		return new StreamsProxy();
	}
	
	@Override
	public ILaunch getLaunch() {
		return launch;
	}
	
	@Override
	public String getLabel() {
		return Constants.LANGUAGE_NAME + " Interpreter";
	}
	
	@Override
	public int getExitValue() throws DebugException {
		if (thread.isAlive())
			throw new DebugException(new Status(IStatus.ERROR, Plugin.ID, "Tried to call getExitValue on running process"));
		// TODO Auto-generated method stub
		return 0;
	}
	
}
