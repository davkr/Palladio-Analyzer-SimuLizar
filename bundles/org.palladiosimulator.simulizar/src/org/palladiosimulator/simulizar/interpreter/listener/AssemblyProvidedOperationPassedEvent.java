package org.palladiosimulator.simulizar.interpreter.listener;

import org.palladiosimulator.pcm.core.composition.AssemblyContext;
import org.palladiosimulator.pcm.repository.ProvidedRole;
import org.palladiosimulator.pcm.repository.Signature;

import de.uka.ipd.sdq.simucomframework.SimuComSimProcess;

public class AssemblyProvidedOperationPassedEvent<R extends ProvidedRole, S extends Signature> extends ModelElementPassedEvent<R> {

    private final AssemblyContext assemblyContext;
    private final S signature;

    public AssemblyProvidedOperationPassedEvent(final R modelElement, final EventType eventType, final SimuComSimProcess thread,
            final S signature, final AssemblyContext ofAssemblyContext) {
        super(modelElement, eventType, thread);
	this.signature = signature;
        this.assemblyContext = ofAssemblyContext;
    }

    /**
     * @return the assemblyContext
     */
    public AssemblyContext getAssemblyContext() {
        return this.assemblyContext;
    }

    public S getSignature() {
        return signature;
    }
}
