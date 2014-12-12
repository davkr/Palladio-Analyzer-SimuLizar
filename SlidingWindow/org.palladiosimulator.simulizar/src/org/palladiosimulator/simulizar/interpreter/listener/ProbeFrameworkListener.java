package org.palladiosimulator.simulizar.interpreter.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.palladiosimulator.edp2.models.measuringpoint.MeasuringPoint;
import org.palladiosimulator.edp2.models.measuringpoint.MeasuringpointFactory;
import org.palladiosimulator.edp2.models.measuringpoint.StringMeasuringPoint;
import org.palladiosimulator.measurementframework.listener.IMeasurementSourceListener;
import org.palladiosimulator.pcmmeasuringpoint.ActiveResourceMeasuringPoint;
import org.palladiosimulator.pcmmeasuringpoint.AssemblyOperationMeasuringPoint;
import org.palladiosimulator.pcmmeasuringpoint.PcmmeasuringpointFactory;
import org.palladiosimulator.pcmmeasuringpoint.SystemOperationMeasuringPoint;
import org.palladiosimulator.pcmmeasuringpoint.UsageScenarioMeasuringPoint;
import org.palladiosimulator.probeframework.calculator.Calculator;
import org.palladiosimulator.probeframework.calculator.ICalculatorFactory;
import org.palladiosimulator.probeframework.probes.Probe;
import org.palladiosimulator.probeframework.probes.TriggeredProbe;
import org.palladiosimulator.simulizar.access.IModelAccess;
import org.palladiosimulator.simulizar.metrics.aggregators.ResponseTimeAggregator;
import org.palladiosimulator.simulizar.pms.MeasurementSpecification;
import org.palladiosimulator.simulizar.pms.PMSModel;
import org.palladiosimulator.simulizar.pms.PerformanceMeasurement;
import org.palladiosimulator.simulizar.pms.PerformanceMetricEnum;
import org.palladiosimulator.simulizar.prm.PRMModel;
import org.palladiosimulator.simulizar.utils.PMSUtil;

import de.uka.ipd.sdq.pcm.core.entity.Entity;
import de.uka.ipd.sdq.pcm.core.entity.InterfaceProvidingEntity;
import de.uka.ipd.sdq.pcm.resourceenvironment.ResourceContainer;
import de.uka.ipd.sdq.pcm.seff.ExternalCallAction;
import de.uka.ipd.sdq.pcm.usagemodel.EntryLevelSystemCall;
import de.uka.ipd.sdq.pcm.usagemodel.UsageScenario;
import de.uka.ipd.sdq.simucomframework.model.SimuComModel;
import de.uka.ipd.sdq.simucomframework.probes.TakeCurrentSimulationTimeProbe;

/**
 * Class for listening to interpreter events in order to store collected data using the
 * ProbeFramework
 * 
 * @author Steffen Becker, Sebastian Lehrig
 */
public class ProbeFrameworkListener extends AbstractInterpreterListener {

    private static final Logger LOGGER = Logger.getLogger(ProbeFrameworkListener.class);
    private static final int START_PROBE_INDEX = 0;
    private static final int STOP_PROBE_INDEX = 1;

    private final PMSModel pmsModel;
    private final PRMModel prmModel;
    private final SimuComModel simuComModel;
    private final ICalculatorFactory calculatorFactory;

    private final Map<String, List<TriggeredProbe>> currentTimeProbes = new HashMap<String, List<TriggeredProbe>>();
    private TriggeredProbe reconfTimeProbe;

    /** Default EMF factory for measuring points. */
    private final MeasuringpointFactory measuringpointFactory = MeasuringpointFactory.eINSTANCE;
    private final PcmmeasuringpointFactory pcmMeasuringpointFactory = PcmmeasuringpointFactory.eINSTANCE;

    /**
     * @param modelAccessFactory
     *            Provides access to simulated models
     * @param simuComModel
     *            Provides access to the central simulation
     */
    public ProbeFrameworkListener(final IModelAccess modelAccessFactory, final SimuComModel simuComModel) {
        super();
        this.pmsModel = modelAccessFactory.getPMSModel();
        this.prmModel = modelAccessFactory.getPRMModel();
        this.calculatorFactory = simuComModel.getProbeFrameworkContext().getCalculatorFactory();
        this.simuComModel = simuComModel;
        this.reconfTimeProbe = null;

        initReponseTimeMeasurement();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.interpreter.interpreter.listener.AbstractInterpreterListener#
     * beginUsageScenarioInterpretation
     * (de.upb.pcm.interpreter.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void beginUsageScenarioInterpretation(final ModelElementPassedEvent<UsageScenario> event) {
        this.startMeasurement(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.interpreter.interpreter.listener.AbstractInterpreterListener#
     * endUsageScenarioInterpretation
     * (de.upb.pcm.interpreter.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void endUsageScenarioInterpretation(final ModelElementPassedEvent<UsageScenario> event) {
        this.endMeasurement(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.interpreter.interpreter.listener.AbstractInterpreterListener#
     * beginEntryLevelSystemCallInterpretation
     * (de.upb.pcm.interpreter.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void beginEntryLevelSystemCallInterpretation(final ModelElementPassedEvent<EntryLevelSystemCall> event) {
        this.startMeasurement(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.interpreter.interpreter.listener.AbstractInterpreterListener#
     * endEntryLevelSystemCallInterpretation
     * (de.upb.pcm.interpreter.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void endEntryLevelSystemCallInterpretation(final ModelElementPassedEvent<EntryLevelSystemCall> event) {
        this.endMeasurement(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.simulizar.interpreter.listener.AbstractInterpreterListener#
     * beginExternalCallInterpretation
     * (de.upb.pcm.simulizar.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void beginExternalCallInterpretation(final RDSEFFElementPassedEvent<ExternalCallAction> event) {
        this.startMeasurement(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.upb.pcm.simulizar.interpreter.listener.AbstractInterpreterListener#
     * endExternalCallInterpretation
     * (de.upb.pcm.simulizar.interpreter.listener.ModelElementPassedEvent)
     */
    @Override
    public void endExternalCallInterpretation(final RDSEFFElementPassedEvent<ExternalCallAction> event) {
        this.endMeasurement(event);
    }

    @Override
    public <T extends EObject> void beginUnknownElementInterpretation(ModelElementPassedEvent<T> event) {
    }

    @Override
    public <T extends EObject> void endUnknownElementInterpretation(ModelElementPassedEvent<T> event) {
    }

    /**
     * Initialize the response time measurements. First gets the monitored elements from the PMS
     * model, create according calculators, and aggregators.
     * 
     */
    private void initReponseTimeMeasurement() {

        if (pmsModel != null) {

            for (final PerformanceMeasurement performanceMeasurement : pmsModel.getPerformanceMeasurements()) {

                final MeasuringPoint measuringPoint = performanceMeasurement.getMeasuringPoint();
                final EObject modelElement = PMSUtil.getMonitoredElement(measuringPoint);

                MeasurementSpecification measurementSpecification = null;
                for (final MeasurementSpecification measurementSpec : performanceMeasurement
                        .getMeasurementSpecification()) {
                    if (measurementSpec.getPerformanceMetric() == PerformanceMetricEnum.RESPONSE_TIME) {
                        measurementSpecification = measurementSpec;
                    }
                }

                if (measurementSpecification != null) {
                    final List<Probe> probeList = createStartAndStopProbe(modelElement, this.simuComModel);
                    final Calculator calculator = calculatorFactory.buildResponseTimeCalculator(measuringPoint,
                            probeList);

                    try {
                        final IMeasurementSourceListener aggregator = new ResponseTimeAggregator(simuComModel,
                                this.prmModel, measurementSpecification, modelElement);
                        calculator.addObserver(aggregator);
                    } catch (final UnsupportedOperationException e) {
                        LOGGER.error(e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }

    }

    /**
     * FIXME This method is not required anymore IF all types measuring points can be modeled
     * 
     * @param modelElement
     *            for which a MeasuringPoint shall be created
     * @param <T>
     *            extends Entity
     * @return MeasuringPoint for modelElement
     */
    private <T extends Entity> MeasuringPoint createMeasuringPoint(EObject modelElement) {

        MeasuringPoint result;
        if (modelElement == null) {
            throw new IllegalArgumentException("ModelElementPassedEvent cannot be null");
        } else if (modelElement instanceof ResourceContainer) {
            final ResourceContainer resourceContainer = (ResourceContainer) modelElement;

            // FIXME Always takes the first active resource of a given container. That should be
            // more flexible. [Lehrig]
            final ActiveResourceMeasuringPoint mp = this.pcmMeasuringpointFactory.createActiveResourceMeasuringPoint();
            mp.setActiveResource(resourceContainer.getActiveResourceSpecifications_ResourceContainer().get(0));
            mp.setReplicaID(0);
            result = mp;
        } else if (modelElement instanceof ExternalCallAction) {
            final ExternalCallAction externalCallAction = (ExternalCallAction) modelElement;

            AssemblyOperationMeasuringPoint mp = this.pcmMeasuringpointFactory.createAssemblyOperationMeasuringPoint();
            // FIXME How can I get the AssemblyContext from the monitored Element in advanced
            // (before running the simulation)
            // mp.setAssembly(((RDSEFFElementPassedEvent<ExternalCallAction>)
            // event).getAssemblyContext());
            mp.setOperationSignature(externalCallAction.getCalledService_ExternalService());
            mp.setRole(externalCallAction.getRole_ExternalService());
            result = mp;
        } else if (modelElement instanceof EntryLevelSystemCall) {
            final EntryLevelSystemCall entryLevelSystemCall = (EntryLevelSystemCall) modelElement;

            final SystemOperationMeasuringPoint mp = this.pcmMeasuringpointFactory
                    .createSystemOperationMeasuringPoint();
            final InterfaceProvidingEntity providingEntity = entryLevelSystemCall
                    .getProvidedRole_EntryLevelSystemCall().getProvidingEntity_ProvidedRole();
            if (providingEntity instanceof de.uka.ipd.sdq.pcm.system.System) {
                de.uka.ipd.sdq.pcm.system.System system = (de.uka.ipd.sdq.pcm.system.System) providingEntity;
                mp.setSystem(system);
            } else {
                throw new IllegalArgumentException("EntryLevelSystemCall \"" + entryLevelSystemCall.getEntityName()
                        + "\" does not reference a system.");
            }
            mp.setOperationSignature(entryLevelSystemCall.getOperationSignature__EntryLevelSystemCall());
            mp.setRole(entryLevelSystemCall.getProvidedRole_EntryLevelSystemCall());
            result = mp;
        } else if (modelElement instanceof UsageScenario) {
            final UsageScenario usageScenario = (UsageScenario) modelElement;

            final UsageScenarioMeasuringPoint mp = this.pcmMeasuringpointFactory.createUsageScenarioMeasuringPoint();
            mp.setUsageScenario(usageScenario);
            result = mp;
        } else {
            throw new IllegalArgumentException("Unknown model element  (" + modelElement.toString() + ")");
        }
        return result;
    }

    /**
     * @param modelElement
     * @param simuComModel
     * @return list with start and stop probe
     */
    @SuppressWarnings({
            "rawtypes", "unchecked"
    })
    protected List<Probe> createStartAndStopProbe(final EObject modelElement, final SimuComModel simuComModel) {
        final List probeList = new ArrayList<TriggeredProbe>(2);
        probeList.add(new TakeCurrentSimulationTimeProbe(simuComModel.getSimulationControl()));
        probeList.add(new TakeCurrentSimulationTimeProbe(simuComModel.getSimulationControl()));
        currentTimeProbes.put(((Entity) modelElement).getId(), Collections.unmodifiableList(probeList));
        return probeList;
    }

    /**
     * @param measurementSpecification
     *            the measurement specification to check
     * @return true if it is monitored
     */
    private boolean isMonitored(final MeasurementSpecification measurementSpecification) {
        return measurementSpecification != null;
    }

    /**
     * @param modelElement
     * @return
     */
    protected boolean entityIsAlreadyInstrumented(final EObject modelElement) {
        return this.currentTimeProbes.containsKey(((Entity) modelElement).getId());
    }

    /**
     * @param <T>
     * @param event
     */
    private <T extends Entity> void startMeasurement(final ModelElementPassedEvent<T> event) {
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId()) && simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(START_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    /**
     * @param event
     */
    private <T extends Entity> void endMeasurement(final ModelElementPassedEvent<T> event) {
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId()) && simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(STOP_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    @Override
    public void reconfigurationInterpretation(final ReconfigurationEvent event) {
        if (this.reconfTimeProbe == null) {
            initReconfTimeMeasurement(event);
        }

        this.reconfTimeProbe.takeMeasurement();
    }

    /**
     * Initializes reconfiguration time measurement.
     * 
     * TODO StringMeasuringPoint should not be used by SimuLizar. Create something better! I could
     * imagine an EDP2 extension that introduces dedicated reconfiguration measuring points.
     * [Lehrig]
     * 
     * FIXME Dead code; no measurements taken here! Needs some more refactorings. [Lehrig]
     * 
     * @param event
     *            which was fired
     * @param <T>
     *            extends Entity
     */
    private <T extends Entity> void initReconfTimeMeasurement(final ReconfigurationEvent event) {
        this.reconfTimeProbe = new TakeCurrentSimulationTimeProbe(this.simuComModel.getSimulationControl());

        final StringMeasuringPoint measuringPoint = measuringpointFactory.createStringMeasuringPoint();
        measuringPoint.setMeasuringPoint("Reconfiguration");
        // this.calculatorFactory.buildStateOfActiveResourceCalculator(measuringPoint,
        // this.reconfTimeProbe);
    }

    private Boolean simulationIsRunning() {
        return this.simuComModel.getSimulationControl().isRunning();
    }
}