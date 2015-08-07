package org.palladiosimulator.simulizar.reconfiguration;

import org.palladiosimulator.simulizar.access.IModelAccess;
import org.palladiosimulator.simulizar.runconfig.SimuLizarWorkflowConfiguration;

public abstract class AbstractReconfigurator implements IReconfigurationEngine {

    protected IModelAccess modelAccessFactory;
    protected SimuLizarWorkflowConfiguration configuration;

    @Override
    public void setModelAccess(IModelAccess modelAccess) {
        if (modelAccess == null) {
            throw new IllegalArgumentException("Given modelAccess must not be null.");
        }
        this.modelAccessFactory = modelAccess;
    }

    @Override
    public void setConfiguration(SimuLizarWorkflowConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Given configuration must not be null.");
        }
        this.configuration = configuration;
    }

}