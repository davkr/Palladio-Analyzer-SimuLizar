package org.palladiosimulator.simulizar.interpreter.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.palladiosimulator.commons.eclipseutils.ExtensionHelper;
import org.palladiosimulator.edp2.models.measuringpoint.MeasuringPoint;
import org.palladiosimulator.edp2.util.MetricDescriptionUtility;
import org.palladiosimulator.metricspec.MetricDescription;
import org.palladiosimulator.metricspec.constants.MetricDescriptionConstants;
import org.palladiosimulator.monitorrepository.MeasurementSpecification;
import org.palladiosimulator.monitorrepository.Monitor;
import org.palladiosimulator.monitorrepository.MonitorRepository;
import org.palladiosimulator.monitorrepository.MonitorRepositoryPackage;
import org.palladiosimulator.monitorrepository.ProcessingType;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.repository.OperationSignature;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;
import org.palladiosimulator.pcm.usagemodel.UsageScenario;
import org.palladiosimulator.probeframework.calculator.ICalculatorFactory;
import org.palladiosimulator.probeframework.probes.Probe;
import org.palladiosimulator.probeframework.probes.TriggeredProbe;
import org.palladiosimulator.runtimemeasurement.RuntimeMeasurementModel;
import org.palladiosimulator.simulizar.access.IModelAccess;
import org.palladiosimulator.simulizar.reconfiguration.Reconfigurator;
import org.palladiosimulator.simulizar.utils.MonitorRepositoryUtil;

import de.uka.ipd.sdq.simucomframework.model.SimuComModel;
import de.uka.ipd.sdq.simucomframework.probes.TakeCurrentSimulationTimeProbe;

/**
 * Class for listening to interpreter events in order to store collected data using the
 * ProbeFramework
 *
 * @author Steffen Becker, Sebastian Lehrig, Florian Rosenthal
 */
public abstract class AbstractProbeFrameworkListener extends AbstractInterpreterListener {

    private static final int START_PROBE_INDEX = 0;
    private static final int STOP_PROBE_INDEX = 1;

    protected final SimuComModel simuComModel;
    protected final ICalculatorFactory calculatorFactory;
    protected final Reconfigurator reconfigurator;
    private final IModelAccess modelAccess;

    private final Map<String, List<TriggeredProbe>> currentTimeProbes = new HashMap<String, List<TriggeredProbe>>();

    /**
     * @param modelAccessFactory
     *            Provides access to simulated models
     * @param simuComModel
     *            Provides access to the central simulation
     */
    public AbstractProbeFrameworkListener(final IModelAccess modelAccess, final SimuComModel simuComModel,
            final Reconfigurator reconfigurator) {
        super();
        this.modelAccess = Objects.requireNonNull(modelAccess);
        this.calculatorFactory = Objects.requireNonNull(simuComModel).getProbeFrameworkContext().getCalculatorFactory();
        this.simuComModel = simuComModel;
        this.reconfigurator = Objects.requireNonNull(reconfigurator);

        this.initResponseTimeMeasurements();
        this.initReconfigurationTimeMeasurement();
        this.initExtensionMeasurements();
    }

    private void initExtensionMeasurements() {
        Iterable<AbstractRecordingProbeFrameworkListenerDecorator> extensions = ExtensionHelper.getExecutableExtensions(
                "org.palladiosimulator.simulizar.interpreter.listener.probeframework", "decorator");
        for (AbstractRecordingProbeFrameworkListenerDecorator decorator : extensions) {
            decorator.setProbeFrameworkListener(this);
            decorator.registerMeasurements();
        }
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
    public <T extends EObject> void beginUnknownElementInterpretation(final ModelElementPassedEvent<T> event) {
    }

    @Override
    public <T extends EObject> void endUnknownElementInterpretation(final ModelElementPassedEvent<T> event) {
    }

    /**
     * Gets the {@link SimuComModel} which is related to this instance.
     *
     * @return A reference to the {@link SimuComModel} currently in use.
     */
    public SimuComModel getSimuComModel() {
        return this.simuComModel;
    }

    /**
     * Gets the {@link IModelAccess} attached to this instance.
     * 
     * @return A reference to the {@code IModelAccess}.
     */
    public IModelAccess getModelAccess() {
        return this.modelAccess;
    }

    /**
     * Gets the {@link RuntimeMeasurementModel} attached to this instance.
     *
     * @return A reference to the {@code RuntimeMeasurementModel}.
     */
    public RuntimeMeasurementModel getRuntimeMeasurementModel() {
        return this.modelAccess.getRuntimeMeasurementModel();
    }

    /**
     * Gets the {@link ICalculatorFactory} that is by this instance during the current simulation
     * run.
     *
     * @return A reference to the {@code ICalculatorFactory}.
     */
    public ICalculatorFactory getCalculatorFactory() {
        return this.calculatorFactory;
    }

    /**
     * Gets all associated {@link MeasurementSpecification}s of <b>active</b> {@link Monitor}s that
     * adhere to the given metric.
     *
     * @param soughtFor
     *            A {@link MetricDescription} denoting the target metric to look for.
     * @return An UNMODIFIABLE {@link Collection} containing all found measurement specifications,
     *         which might be empty but never {@code null}.
     */
    public Collection<MeasurementSpecification> getMeasurementSpecificationsForMetricDescription(
            final MetricDescription soughtFor) {
        Objects.requireNonNull(soughtFor, "Given MetricDescription must not be null.");

        return filterMeasurementSpecifications(
                m -> MetricDescriptionUtility.metricDescriptionIdsEqual(m.getMetricDescription(), soughtFor));
    }

    /**
     * Gets all associated {@link MeasurementSpecification}s of <b>active</b> {@link Monitor}s whose
     * 'processingType' attribute is of a certain type.
     *
     * @param processingTypeEClass
     *            An {@link EClass} denoting the {@link ProcessingType} subclass to look for.
     * @return An UNMODIFIABLE {@link Collection} containing all found measurement specifications,
     *         which might be empty (for instance if none are found) but never {@code null}.
     * @throws NullPointerException
     *             In case {@code processingTypeEClass == null}.
     * @throws IllegalArgumentException
     *             In case the passed EClass does not represent a valid {@link ProcessingType}.
     */
    public Collection<MeasurementSpecification> getMeasurementSpecificationsForProcessingType(
            final EClass processingTypeEClass) {
        Objects.requireNonNull(processingTypeEClass, "Given EClass object must not be null.");
        if (!MonitorRepositoryPackage.Literals.PROCESSING_TYPE.isSuperTypeOf(processingTypeEClass)) {
            throw new IllegalArgumentException("Given EClass object does not represent a "
                    + MonitorRepositoryPackage.Literals.PROCESSING_TYPE.getName() + "!");
        }
        return filterMeasurementSpecifications(m -> processingTypeEClass.isInstance(m.getProcessingType()));
    }

    /**
     * Executes a filter, given by the passed predicate expression, on all the measurement
     * specifications of active {@link Monitor}s, i.e., monitors for which
     * {@link Monitor#isActivated()} returns {@code true}, contained in the associated
     * {@link MonitorRepository}.
     * 
     * @param predicate
     *            The filter {@link Predicate}.
     * @return An UNMODIFIABLE {@link Collection} containing all found measurement specifications,
     *         which might be empty but never {@code null}.
     */
    private Collection<MeasurementSpecification> filterMeasurementSpecifications(
            final Predicate<? super MeasurementSpecification> predicate) {
        assert predicate != null;

        MonitorRepository monitorRepositoryModel = this.modelAccess.getMonitorRepositoryModel();
        if (monitorRepositoryModel != null) {
            return monitorRepositoryModel.getMonitors().stream().filter(Monitor::isActivated)
                    .flatMap(monitor -> monitor.getMeasurementSpecifications().stream()).filter(predicate)
                    .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        }
        return Collections.emptyList();
    }

    /**
     * Initializes the <i>response time</i> measurements. First gets the monitored elements from the
     * monitor repository, then creates corresponding calculators.
     *
     */
    private void initResponseTimeMeasurements() {
        for (MeasurementSpecification responseTimeMeasurementSpec : this
                .getMeasurementSpecificationsForMetricDescription(MetricDescriptionConstants.RESPONSE_TIME_METRIC)) {
            MeasuringPoint measuringPoint = responseTimeMeasurementSpec.getMonitor().getMeasuringPoint();
            List<Probe> probeList = this.createStartAndStopProbe(measuringPoint, this.simuComModel);
            this.calculatorFactory.buildResponseTimeCalculator(measuringPoint, probeList);
        }
    }

    /**
     * @param measuringPoint
     * @param simuComModel
     * @return list with start and stop probe
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected List<Probe> createStartAndStopProbe(final MeasuringPoint measuringPoint,
            final SimuComModel simuComModel) {
        final List probeList = new ArrayList<TriggeredProbe>(2);
        probeList.add(new TakeCurrentSimulationTimeProbe(simuComModel.getSimulationControl()));
        probeList.add(new TakeCurrentSimulationTimeProbe(simuComModel.getSimulationControl()));
        final EObject modelElement = MonitorRepositoryUtil.getMonitoredElement(measuringPoint);
        this.currentTimeProbes.put(((Entity) modelElement).getId(), Collections.unmodifiableList(probeList));
        return probeList;
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
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId())
                && this.simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(START_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    /**
     * @param event
     */
    private <T extends Entity> void endMeasurement(final ModelElementPassedEvent<T> event) {
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId())
                && this.simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(STOP_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    @Override
    public void beginSystemOperationCallInterpretation(final ModelElementPassedEvent<OperationSignature> event) {
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId())
                && this.simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(START_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    @Override
    public void endSystemOperationCallInterpretation(final ModelElementPassedEvent<OperationSignature> event) {
        if (this.currentTimeProbes.containsKey(((Entity) event.getModelElement()).getId())
                && this.simulationIsRunning()) {
            this.currentTimeProbes.get(((Entity) event.getModelElement()).getId()).get(STOP_PROBE_INDEX)
                    .takeMeasurement(event.getThread().getRequestContext());
        }
    }

    /**
     * Initializes reconfiguration time measurement.
     */
    protected abstract void initReconfigurationTimeMeasurement();

    private boolean simulationIsRunning() {
        return this.simuComModel.getSimulationControl().isRunning();
    }
}
