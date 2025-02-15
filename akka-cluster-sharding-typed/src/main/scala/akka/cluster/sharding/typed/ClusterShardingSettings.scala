/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.sharding.typed

import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.Config

import akka.actor.typed.ActorSystem
import akka.annotation.{ ApiMayChange, InternalApi }
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.sharding.typed.ClusterShardingSettings.RememberEntitiesStoreModeDData
import akka.cluster.sharding.{ ClusterShardingSettings => ClassicShardingSettings }
import akka.cluster.singleton.{ ClusterSingletonManagerSettings => ClassicClusterSingletonManagerSettings }
import akka.cluster.typed.Cluster
import akka.cluster.typed.ClusterSingletonManagerSettings
import akka.coordination.lease.LeaseUsageSettings
import akka.util.JavaDurationConverters._

object ClusterShardingSettings {

  /** Scala API: Creates new cluster sharding settings object */
  def apply(system: ActorSystem[_]): ClusterShardingSettings =
    fromConfig(system.settings.config.getConfig("akka.cluster.sharding"))

  def fromConfig(config: Config): ClusterShardingSettings = {
    val classicSettings = ClassicShardingSettings(config)
    val numberOfShards = config.getInt("number-of-shards")
    fromClassicSettings(numberOfShards, classicSettings)
  }

  /** Java API: Creates new cluster sharding settings object */
  def create(system: ActorSystem[_]): ClusterShardingSettings =
    apply(system)

  /** INTERNAL API: Intended only for internal use, it is not recommended to keep converting between the setting types */
  private[akka] def fromClassicSettings(
      numberOfShards: Int,
      classicSettings: ClassicShardingSettings): ClusterShardingSettings = {
    new ClusterShardingSettings(
      numberOfShards,
      role = classicSettings.role,
      dataCenter = None,
      rememberEntities = classicSettings.rememberEntities,
      journalPluginId = classicSettings.journalPluginId,
      snapshotPluginId = classicSettings.snapshotPluginId,
      passivationStrategySettings = new PassivationStrategySettings(classicSettings.passivationStrategySettings),
      shardRegionQueryTimeout = classicSettings.shardRegionQueryTimeout,
      stateStoreMode = StateStoreMode.byName(classicSettings.stateStoreMode),
      rememberEntitiesStoreMode = RememberEntitiesStoreMode.byName(classicSettings.rememberEntitiesStore),
      new TuningParameters(classicSettings.tuningParameters),
      new ClusterSingletonManagerSettings(
        classicSettings.coordinatorSingletonSettings.singletonName,
        classicSettings.coordinatorSingletonSettings.role,
        classicSettings.coordinatorSingletonSettings.removalMargin,
        classicSettings.coordinatorSingletonSettings.handOverRetryInterval,
        classicSettings.coordinatorSingletonSettings.leaseSettings),
      leaseSettings = classicSettings.leaseSettings)
  }

  /** INTERNAL API: Intended only for internal use, it is not recommended to keep converting between the setting types */
  private[akka] def toClassicSettings(settings: ClusterShardingSettings): ClassicShardingSettings = {
    new ClassicShardingSettings(
      role = settings.role,
      rememberEntities = settings.rememberEntities,
      journalPluginId = settings.journalPluginId,
      snapshotPluginId = settings.snapshotPluginId,
      stateStoreMode = settings.stateStoreMode.name,
      rememberEntitiesStore = settings.rememberEntitiesStoreMode.name,
      passivationStrategySettings = new ClassicShardingSettings.PassivationStrategySettings(
        strategy = settings.passivationStrategySettings.strategy,
        idleTimeout = settings.passivationStrategySettings.idleTimeout,
        leastRecentlyUsedLimit = settings.passivationStrategySettings.leastRecentlyUsedLimit),
      shardRegionQueryTimeout = settings.shardRegionQueryTimeout,
      new ClassicShardingSettings.TuningParameters(
        bufferSize = settings.tuningParameters.bufferSize,
        coordinatorFailureBackoff = settings.tuningParameters.coordinatorFailureBackoff,
        retryInterval = settings.tuningParameters.retryInterval,
        handOffTimeout = settings.tuningParameters.handOffTimeout,
        shardStartTimeout = settings.tuningParameters.shardStartTimeout,
        shardFailureBackoff = settings.tuningParameters.shardFailureBackoff,
        entityRestartBackoff = settings.tuningParameters.entityRestartBackoff,
        rebalanceInterval = settings.tuningParameters.rebalanceInterval,
        snapshotAfter = settings.tuningParameters.snapshotAfter,
        keepNrOfBatches = settings.tuningParameters.keepNrOfBatches,
        leastShardAllocationRebalanceThreshold = settings.tuningParameters.leastShardAllocationRebalanceThreshold, // TODO extract it a bit
        leastShardAllocationMaxSimultaneousRebalance =
          settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance,
        waitingForStateTimeout = settings.tuningParameters.waitingForStateTimeout,
        updatingStateTimeout = settings.tuningParameters.updatingStateTimeout,
        entityRecoveryStrategy = settings.tuningParameters.entityRecoveryStrategy,
        entityRecoveryConstantRateStrategyFrequency =
          settings.tuningParameters.entityRecoveryConstantRateStrategyFrequency,
        entityRecoveryConstantRateStrategyNumberOfEntities =
          settings.tuningParameters.entityRecoveryConstantRateStrategyNumberOfEntities,
        coordinatorStateWriteMajorityPlus = settings.tuningParameters.coordinatorStateWriteMajorityPlus,
        coordinatorStateReadMajorityPlus = settings.tuningParameters.coordinatorStateReadMajorityPlus,
        leastShardAllocationAbsoluteLimit = settings.tuningParameters.leastShardAllocationAbsoluteLimit,
        leastShardAllocationRelativeLimit = settings.tuningParameters.leastShardAllocationRelativeLimit),
      new ClassicClusterSingletonManagerSettings(
        settings.coordinatorSingletonSettings.singletonName,
        settings.coordinatorSingletonSettings.role,
        settings.coordinatorSingletonSettings.removalMargin,
        settings.coordinatorSingletonSettings.handOverRetryInterval,
        settings.coordinatorSingletonSettings.leaseSettings),
      leaseSettings = settings.leaseSettings)

  }

  private def option(role: String): Option[String] =
    if (role == "" || role == null) None else Option(role)

  sealed trait StateStoreMode { def name: String }

  /**
   * Java API
   */
  def stateStoreModePersistence(): StateStoreMode = StateStoreModePersistence

  /**
   * Java API
   */
  def stateStoreModeDdata(): StateStoreMode = StateStoreModePersistence

  object StateStoreMode {

    def byName(name: String): StateStoreMode =
      if (name == StateStoreModePersistence.name) StateStoreModePersistence
      else if (name == StateStoreModeDData.name) StateStoreModeDData
      else
        throw new IllegalArgumentException(
          s"Not recognized StateStoreMode, only '${StateStoreModePersistence.name}' and '${StateStoreModeDData.name}' are supported.")
  }

  case object StateStoreModePersistence extends StateStoreMode { override def name = "persistence" }

  case object StateStoreModeDData extends StateStoreMode { override def name = "ddata" }

  /**
   * Java API
   */
  def rememberEntitiesStoreModeEventSourced(): RememberEntitiesStoreMode = RememberEntitiesStoreModeEventSourced

  /**
   * Java API
   */
  def rememberEntitiesStoreModeDdata(): RememberEntitiesStoreMode = RememberEntitiesStoreModeDData

  sealed trait RememberEntitiesStoreMode { def name: String }

  object RememberEntitiesStoreMode {

    def byName(name: String): RememberEntitiesStoreMode =
      if (name == RememberEntitiesStoreModeEventSourced.name) RememberEntitiesStoreModeEventSourced
      else if (name == RememberEntitiesStoreModeDData.name) RememberEntitiesStoreModeDData
      else
        throw new IllegalArgumentException(
          s"Not recognized RememberEntitiesStore, only '${RememberEntitiesStoreModeDData.name}' and '${RememberEntitiesStoreModeEventSourced.name}' are supported.")
  }
  case object RememberEntitiesStoreModeEventSourced extends RememberEntitiesStoreMode {
    override def name = "eventsourced"
  }
  case object RememberEntitiesStoreModeDData extends RememberEntitiesStoreMode { override def name = "ddata" }

  @ApiMayChange
  final class PassivationStrategySettings private (
      val strategy: String,
      val idleTimeout: FiniteDuration,
      val leastRecentlyUsedLimit: Int,
      private[akka] val oldSettingUsed: Boolean) {

    def this(strategy: String, idleTimeout: FiniteDuration, leastRecentlyUsedLimit: Int) =
      this(strategy, idleTimeout, leastRecentlyUsedLimit, oldSettingUsed = false)

    def this(classic: ClassicShardingSettings.PassivationStrategySettings) =
      this(classic.strategy, classic.idleTimeout, classic.leastRecentlyUsedLimit, classic.oldSettingUsed)

    def withIdleStrategy(timeout: FiniteDuration): PassivationStrategySettings =
      copy(strategy = "idle", idleTimeout = timeout)

    def withLeastRecentlyUsedStrategy(limit: Int): PassivationStrategySettings =
      copy(strategy = "least-recently-used", leastRecentlyUsedLimit = limit)

    private[akka] def withOldIdleStrategy(timeout: FiniteDuration): PassivationStrategySettings =
      copy(strategy = "idle", idleTimeout = timeout, oldSettingUsed = true)

    private def copy(
        strategy: String,
        idleTimeout: FiniteDuration = idleTimeout,
        leastRecentlyUsedLimit: Int = leastRecentlyUsedLimit,
        oldSettingUsed: Boolean = oldSettingUsed): PassivationStrategySettings =
      new PassivationStrategySettings(strategy, idleTimeout, leastRecentlyUsedLimit, oldSettingUsed)
  }

  object PassivationStrategySettings {
    val disabled = new PassivationStrategySettings(
      strategy = "none",
      idleTimeout = Duration.Zero,
      leastRecentlyUsedLimit = 0,
      oldSettingUsed = false)

    def oldDefault(idleTimeout: FiniteDuration): PassivationStrategySettings =
      disabled.withOldIdleStrategy(idleTimeout)
  }

  // generated using kaze-class
  final class TuningParameters private (
      val bufferSize: Int,
      val coordinatorFailureBackoff: FiniteDuration,
      val entityRecoveryConstantRateStrategyFrequency: FiniteDuration,
      val entityRecoveryConstantRateStrategyNumberOfEntities: Int,
      val entityRecoveryStrategy: String,
      val entityRestartBackoff: FiniteDuration,
      val handOffTimeout: FiniteDuration,
      val keepNrOfBatches: Int,
      val leastShardAllocationMaxSimultaneousRebalance: Int,
      val leastShardAllocationRebalanceThreshold: Int,
      val rebalanceInterval: FiniteDuration,
      val retryInterval: FiniteDuration,
      val shardFailureBackoff: FiniteDuration,
      val shardStartTimeout: FiniteDuration,
      val snapshotAfter: Int,
      val updatingStateTimeout: FiniteDuration,
      val waitingForStateTimeout: FiniteDuration,
      val coordinatorStateWriteMajorityPlus: Int,
      val coordinatorStateReadMajorityPlus: Int,
      val leastShardAllocationAbsoluteLimit: Int,
      val leastShardAllocationRelativeLimit: Double) {

    def this(classic: ClassicShardingSettings.TuningParameters) =
      this(
        bufferSize = classic.bufferSize,
        coordinatorFailureBackoff = classic.coordinatorFailureBackoff,
        retryInterval = classic.retryInterval,
        handOffTimeout = classic.handOffTimeout,
        shardStartTimeout = classic.shardStartTimeout,
        shardFailureBackoff = classic.shardFailureBackoff,
        entityRestartBackoff = classic.entityRestartBackoff,
        rebalanceInterval = classic.rebalanceInterval,
        snapshotAfter = classic.snapshotAfter,
        keepNrOfBatches = classic.keepNrOfBatches,
        leastShardAllocationRebalanceThreshold = classic.leastShardAllocationRebalanceThreshold, // TODO extract it a bit
        leastShardAllocationMaxSimultaneousRebalance = classic.leastShardAllocationMaxSimultaneousRebalance,
        waitingForStateTimeout = classic.waitingForStateTimeout,
        updatingStateTimeout = classic.updatingStateTimeout,
        entityRecoveryStrategy = classic.entityRecoveryStrategy,
        entityRecoveryConstantRateStrategyFrequency = classic.entityRecoveryConstantRateStrategyFrequency,
        entityRecoveryConstantRateStrategyNumberOfEntities = classic.entityRecoveryConstantRateStrategyNumberOfEntities,
        coordinatorStateWriteMajorityPlus = classic.coordinatorStateWriteMajorityPlus,
        coordinatorStateReadMajorityPlus = classic.coordinatorStateReadMajorityPlus,
        leastShardAllocationAbsoluteLimit = classic.leastShardAllocationAbsoluteLimit,
        leastShardAllocationRelativeLimit = classic.leastShardAllocationRelativeLimit)

    require(
      entityRecoveryStrategy == "all" || entityRecoveryStrategy == "constant",
      s"Unknown 'entity-recovery-strategy' [$entityRecoveryStrategy], valid values are 'all' or 'constant'")

    def withBufferSize(value: Int): TuningParameters = copy(bufferSize = value)
    def withCoordinatorFailureBackoff(value: FiniteDuration): TuningParameters = copy(coordinatorFailureBackoff = value)
    def withCoordinatorFailureBackoff(value: java.time.Duration): TuningParameters =
      withCoordinatorFailureBackoff(value.asScala)
    def withEntityRecoveryConstantRateStrategyFrequency(value: FiniteDuration): TuningParameters =
      copy(entityRecoveryConstantRateStrategyFrequency = value)
    def withEntityRecoveryConstantRateStrategyFrequency(value: java.time.Duration): TuningParameters =
      withEntityRecoveryConstantRateStrategyFrequency(value.asScala)
    def withEntityRecoveryConstantRateStrategyNumberOfEntities(value: Int): TuningParameters =
      copy(entityRecoveryConstantRateStrategyNumberOfEntities = value)
    def withEntityRecoveryStrategy(value: java.lang.String): TuningParameters = copy(entityRecoveryStrategy = value)
    def withEntityRestartBackoff(value: FiniteDuration): TuningParameters = copy(entityRestartBackoff = value)
    def withEntityRestartBackoff(value: java.time.Duration): TuningParameters = withEntityRestartBackoff(value.asScala)
    def withHandOffTimeout(value: FiniteDuration): TuningParameters = copy(handOffTimeout = value)
    def withHandOffTimeout(value: java.time.Duration): TuningParameters = withHandOffTimeout(value.asScala)
    def withKeepNrOfBatches(value: Int): TuningParameters = copy(keepNrOfBatches = value)
    def withLeastShardAllocationMaxSimultaneousRebalance(value: Int): TuningParameters =
      copy(leastShardAllocationMaxSimultaneousRebalance = value)
    def withLeastShardAllocationRebalanceThreshold(value: Int): TuningParameters =
      copy(leastShardAllocationRebalanceThreshold = value)
    def withRebalanceInterval(value: FiniteDuration): TuningParameters = copy(rebalanceInterval = value)
    def withRebalanceInterval(value: java.time.Duration): TuningParameters = withRebalanceInterval(value.asScala)
    def withRetryInterval(value: FiniteDuration): TuningParameters = copy(retryInterval = value)
    def withRetryInterval(value: java.time.Duration): TuningParameters = withRetryInterval(value.asScala)
    def withShardFailureBackoff(value: FiniteDuration): TuningParameters = copy(shardFailureBackoff = value)
    def withShardFailureBackoff(value: java.time.Duration): TuningParameters = withShardFailureBackoff(value.asScala)
    def withShardStartTimeout(value: FiniteDuration): TuningParameters = copy(shardStartTimeout = value)
    def withShardStartTimeout(value: java.time.Duration): TuningParameters = withShardStartTimeout(value.asScala)
    def withSnapshotAfter(value: Int): TuningParameters = copy(snapshotAfter = value)
    def withUpdatingStateTimeout(value: FiniteDuration): TuningParameters = copy(updatingStateTimeout = value)
    def withUpdatingStateTimeout(value: java.time.Duration): TuningParameters = withUpdatingStateTimeout(value.asScala)
    def withWaitingForStateTimeout(value: FiniteDuration): TuningParameters = copy(waitingForStateTimeout = value)
    def withWaitingForStateTimeout(value: java.time.Duration): TuningParameters =
      withWaitingForStateTimeout(value.asScala)
    def withCoordinatorStateWriteMajorityPlus(value: Int): TuningParameters =
      copy(coordinatorStateWriteMajorityPlus = value)
    def withCoordinatorStateReadMajorityPlus(value: Int): TuningParameters =
      copy(coordinatorStateReadMajorityPlus = value)
    def withLeastShardAllocationAbsoluteLimit(value: Int): TuningParameters =
      copy(leastShardAllocationAbsoluteLimit = value)
    def withLeastShardAllocationRelativeLimit(value: Double): TuningParameters =
      copy(leastShardAllocationRelativeLimit = value)

    private def copy(
        bufferSize: Int = bufferSize,
        coordinatorFailureBackoff: FiniteDuration = coordinatorFailureBackoff,
        entityRecoveryConstantRateStrategyFrequency: FiniteDuration = entityRecoveryConstantRateStrategyFrequency,
        entityRecoveryConstantRateStrategyNumberOfEntities: Int = entityRecoveryConstantRateStrategyNumberOfEntities,
        entityRecoveryStrategy: java.lang.String = entityRecoveryStrategy,
        entityRestartBackoff: FiniteDuration = entityRestartBackoff,
        handOffTimeout: FiniteDuration = handOffTimeout,
        keepNrOfBatches: Int = keepNrOfBatches,
        leastShardAllocationMaxSimultaneousRebalance: Int = leastShardAllocationMaxSimultaneousRebalance,
        leastShardAllocationRebalanceThreshold: Int = leastShardAllocationRebalanceThreshold,
        rebalanceInterval: FiniteDuration = rebalanceInterval,
        retryInterval: FiniteDuration = retryInterval,
        shardFailureBackoff: FiniteDuration = shardFailureBackoff,
        shardStartTimeout: FiniteDuration = shardStartTimeout,
        snapshotAfter: Int = snapshotAfter,
        updatingStateTimeout: FiniteDuration = updatingStateTimeout,
        waitingForStateTimeout: FiniteDuration = waitingForStateTimeout,
        coordinatorStateWriteMajorityPlus: Int = coordinatorStateWriteMajorityPlus,
        coordinatorStateReadMajorityPlus: Int = coordinatorStateReadMajorityPlus,
        leastShardAllocationAbsoluteLimit: Int = leastShardAllocationAbsoluteLimit,
        leastShardAllocationRelativeLimit: Double = leastShardAllocationRelativeLimit): TuningParameters =
      new TuningParameters(
        bufferSize = bufferSize,
        coordinatorFailureBackoff = coordinatorFailureBackoff,
        entityRecoveryConstantRateStrategyFrequency = entityRecoveryConstantRateStrategyFrequency,
        entityRecoveryConstantRateStrategyNumberOfEntities = entityRecoveryConstantRateStrategyNumberOfEntities,
        entityRecoveryStrategy = entityRecoveryStrategy,
        entityRestartBackoff = entityRestartBackoff,
        handOffTimeout = handOffTimeout,
        keepNrOfBatches = keepNrOfBatches,
        leastShardAllocationMaxSimultaneousRebalance = leastShardAllocationMaxSimultaneousRebalance,
        leastShardAllocationRebalanceThreshold = leastShardAllocationRebalanceThreshold,
        rebalanceInterval = rebalanceInterval,
        retryInterval = retryInterval,
        shardFailureBackoff = shardFailureBackoff,
        shardStartTimeout = shardStartTimeout,
        snapshotAfter = snapshotAfter,
        updatingStateTimeout = updatingStateTimeout,
        waitingForStateTimeout = waitingForStateTimeout,
        coordinatorStateWriteMajorityPlus = coordinatorStateWriteMajorityPlus,
        coordinatorStateReadMajorityPlus = coordinatorStateReadMajorityPlus,
        leastShardAllocationAbsoluteLimit = leastShardAllocationAbsoluteLimit,
        leastShardAllocationRelativeLimit = leastShardAllocationRelativeLimit)

    override def toString =
      s"""TuningParameters($bufferSize,$coordinatorFailureBackoff,$entityRecoveryConstantRateStrategyFrequency,$entityRecoveryConstantRateStrategyNumberOfEntities,$entityRecoveryStrategy,$entityRestartBackoff,$handOffTimeout,$keepNrOfBatches,$leastShardAllocationMaxSimultaneousRebalance,$leastShardAllocationRebalanceThreshold,$rebalanceInterval,$retryInterval,$shardFailureBackoff,$shardStartTimeout,$snapshotAfter,$updatingStateTimeout,$waitingForStateTimeout,$coordinatorStateReadMajorityPlus,$coordinatorStateReadMajorityPlus,$leastShardAllocationAbsoluteLimit,$leastShardAllocationRelativeLimit)"""
  }
}

/**
 * @param numberOfShards number of shards used by the default [[HashCodeMessageExtractor]]
 * @param role Specifies that this entity type requires cluster nodes with a specific role.
 *   If the role is not specified all nodes in the cluster are used. If the given role does
 *   not match the role of the current node the `ShardRegion` will be started in proxy mode.
 * @param dataCenter The data center of the cluster nodes where the cluster sharding is running.
 *   If the dataCenter is not specified then the same data center as current node. If the given
 *   dataCenter does not match the data center of the current node the `ShardRegion` will be started
 *   in proxy mode.
 * @param rememberEntities true if active entity actors shall be automatically restarted upon `Shard`
 *   restart. i.e. if the `Shard` is started on a different `ShardRegion` due to rebalance or crash.
 * @param journalPluginId Absolute path to the journal plugin configuration entity that is to
 *   be used for the internal persistence of ClusterSharding. If not defined the default
 *   journal plugin is used. Note that this is not related to persistence used by the entity
 *   actors.
 * @param passivationStrategySettings settings for automatic passivation strategy, see descriptions in reference.conf
 * @param snapshotPluginId Absolute path to the snapshot plugin configuration entity that is to
 *   be used for the internal persistence of ClusterSharding. If not defined the default
 *   snapshot plugin is used. Note that this is not related to persistence used by the entity
 *   actors.
 * @param tuningParameters additional tuning parameters, see descriptions in reference.conf
 */
final class ClusterShardingSettings(
    val numberOfShards: Int,
    val role: Option[String],
    val dataCenter: Option[DataCenter],
    val rememberEntities: Boolean,
    val journalPluginId: String,
    val snapshotPluginId: String,
    val passivationStrategySettings: ClusterShardingSettings.PassivationStrategySettings,
    val shardRegionQueryTimeout: FiniteDuration,
    val stateStoreMode: ClusterShardingSettings.StateStoreMode,
    val rememberEntitiesStoreMode: ClusterShardingSettings.RememberEntitiesStoreMode,
    val tuningParameters: ClusterShardingSettings.TuningParameters,
    val coordinatorSingletonSettings: ClusterSingletonManagerSettings,
    val leaseSettings: Option[LeaseUsageSettings]) {

  @deprecated("Use constructor with passivationStrategySettings", "2.6.18")
  def this(
      numberOfShards: Int,
      role: Option[String],
      dataCenter: Option[DataCenter],
      rememberEntities: Boolean,
      journalPluginId: String,
      snapshotPluginId: String,
      passivateIdleEntityAfter: FiniteDuration,
      shardRegionQueryTimeout: FiniteDuration,
      stateStoreMode: ClusterShardingSettings.StateStoreMode,
      rememberEntitiesStoreMode: ClusterShardingSettings.RememberEntitiesStoreMode,
      tuningParameters: ClusterShardingSettings.TuningParameters,
      coordinatorSingletonSettings: ClusterSingletonManagerSettings,
      leaseSettings: Option[LeaseUsageSettings]) =
    this(
      numberOfShards,
      role,
      dataCenter,
      rememberEntities,
      journalPluginId,
      snapshotPluginId,
      ClusterShardingSettings.PassivationStrategySettings.oldDefault(passivateIdleEntityAfter),
      shardRegionQueryTimeout,
      stateStoreMode,
      rememberEntitiesStoreMode,
      tuningParameters,
      coordinatorSingletonSettings,
      leaseSettings)

  @deprecated("Use constructor with leaseSettings", "2.6.11")
  def this(
      numberOfShards: Int,
      role: Option[String],
      dataCenter: Option[DataCenter],
      rememberEntities: Boolean,
      journalPluginId: String,
      snapshotPluginId: String,
      passivateIdleEntityAfter: FiniteDuration,
      shardRegionQueryTimeout: FiniteDuration,
      stateStoreMode: ClusterShardingSettings.StateStoreMode,
      rememberEntitiesStoreMode: ClusterShardingSettings.RememberEntitiesStoreMode,
      tuningParameters: ClusterShardingSettings.TuningParameters,
      coordinatorSingletonSettings: ClusterSingletonManagerSettings) =
    this(
      numberOfShards,
      role,
      dataCenter,
      rememberEntities,
      journalPluginId,
      snapshotPluginId,
      passivateIdleEntityAfter,
      shardRegionQueryTimeout,
      stateStoreMode,
      rememberEntitiesStoreMode,
      tuningParameters,
      coordinatorSingletonSettings,
      None)

  @deprecated("Use constructor with rememberEntitiesStoreMode", "2.6.6")
  def this(
      numberOfShards: Int,
      role: Option[String],
      dataCenter: Option[DataCenter],
      rememberEntities: Boolean,
      journalPluginId: String,
      snapshotPluginId: String,
      passivateIdleEntityAfter: FiniteDuration,
      shardRegionQueryTimeout: FiniteDuration,
      stateStoreMode: ClusterShardingSettings.StateStoreMode,
      tuningParameters: ClusterShardingSettings.TuningParameters,
      coordinatorSingletonSettings: ClusterSingletonManagerSettings) =
    this(
      numberOfShards,
      role,
      dataCenter,
      rememberEntities,
      journalPluginId,
      snapshotPluginId,
      passivateIdleEntityAfter,
      shardRegionQueryTimeout,
      stateStoreMode,
      RememberEntitiesStoreModeDData,
      tuningParameters,
      coordinatorSingletonSettings,
      None)

  /**
   * INTERNAL API
   * If true, this node should run the shard region, otherwise just a shard proxy should started on this node.
   * It's checking if the `role` and `dataCenter` are matching.
   */
  @InternalApi
  private[akka] def shouldHostShard(cluster: Cluster): Boolean =
    role.forall(cluster.selfMember.roles.contains) &&
    dataCenter.forall(_ == cluster.selfMember.dataCenter)

  // no withNumberOfShards because it should be defined in configuration to be able to verify same
  // value on all nodes with `JoinConfigCompatChecker`

  def withRole(role: String): ClusterShardingSettings = copy(role = ClusterShardingSettings.option(role))

  def withDataCenter(dataCenter: DataCenter): ClusterShardingSettings =
    copy(dataCenter = ClusterShardingSettings.option(dataCenter))

  def withRememberEntities(rememberEntities: Boolean): ClusterShardingSettings =
    copy(rememberEntities = rememberEntities)

  def withJournalPluginId(journalPluginId: String): ClusterShardingSettings =
    copy(journalPluginId = journalPluginId)

  def withSnapshotPluginId(snapshotPluginId: String): ClusterShardingSettings =
    copy(snapshotPluginId = snapshotPluginId)

  def withTuningParameters(tuningParameters: ClusterShardingSettings.TuningParameters): ClusterShardingSettings =
    copy(tuningParameters = tuningParameters)

  def withStateStoreMode(stateStoreMode: ClusterShardingSettings.StateStoreMode): ClusterShardingSettings =
    copy(stateStoreMode = stateStoreMode)

  def withRememberEntitiesStoreMode(
      rememberEntitiesStoreMode: ClusterShardingSettings.RememberEntitiesStoreMode): ClusterShardingSettings =
    copy(rememberEntitiesStoreMode = rememberEntitiesStoreMode)

  @deprecated("See passivationStrategySettings.idleTimeout instead", since = "2.6.18")
  def passivateIdleEntityAfter: FiniteDuration = passivationStrategySettings.idleTimeout

  @deprecated("Use withIdlePassivationStrategy instead", since = "2.6.18")
  def withPassivateIdleEntityAfter(duration: FiniteDuration): ClusterShardingSettings =
    copy(passivationStrategySettings = passivationStrategySettings.withOldIdleStrategy(duration))

  @deprecated("Use withIdlePassivationStrategy instead", since = "2.6.18")
  def withPassivateIdleEntityAfter(duration: java.time.Duration): ClusterShardingSettings =
    copy(passivationStrategySettings = passivationStrategySettings.withOldIdleStrategy(duration.asScala))

  def withIdlePassivationStrategy(timeout: FiniteDuration): ClusterShardingSettings =
    copy(passivationStrategySettings = passivationStrategySettings.withIdleStrategy(timeout))

  def withIdlePassivationStrategy(timeout: java.time.Duration): ClusterShardingSettings =
    withIdlePassivationStrategy(timeout.asScala)

  def withLeastRecentlyUsedPassivationStrategy(limit: Int): ClusterShardingSettings =
    copy(passivationStrategySettings = passivationStrategySettings.withLeastRecentlyUsedStrategy(limit))

  def withShardRegionQueryTimeout(duration: FiniteDuration): ClusterShardingSettings =
    copy(shardRegionQueryTimeout = duration)

  def withShardRegionQueryTimeout(duration: java.time.Duration): ClusterShardingSettings =
    copy(shardRegionQueryTimeout = duration.asScala)

  def withLeaseSettings(leaseSettings: LeaseUsageSettings) = copy(leaseSettings = Option(leaseSettings))

  /**
   * The `role` of the `ClusterSingletonManagerSettings` is not used. The `role` of the
   * coordinator singleton will be the same as the `role` of `ClusterShardingSettings`.
   */
  def withCoordinatorSingletonSettings(
      coordinatorSingletonSettings: ClusterSingletonManagerSettings): ClusterShardingSettings =
    copy(coordinatorSingletonSettings = coordinatorSingletonSettings)

  private def copy(
      role: Option[String] = role,
      dataCenter: Option[DataCenter] = dataCenter,
      rememberEntities: Boolean = rememberEntities,
      journalPluginId: String = journalPluginId,
      snapshotPluginId: String = snapshotPluginId,
      stateStoreMode: ClusterShardingSettings.StateStoreMode = stateStoreMode,
      rememberEntitiesStoreMode: ClusterShardingSettings.RememberEntitiesStoreMode = rememberEntitiesStoreMode,
      tuningParameters: ClusterShardingSettings.TuningParameters = tuningParameters,
      coordinatorSingletonSettings: ClusterSingletonManagerSettings = coordinatorSingletonSettings,
      passivationStrategySettings: ClusterShardingSettings.PassivationStrategySettings = passivationStrategySettings,
      shardRegionQueryTimeout: FiniteDuration = shardRegionQueryTimeout,
      leaseSettings: Option[LeaseUsageSettings] = leaseSettings): ClusterShardingSettings =
    new ClusterShardingSettings(
      numberOfShards,
      role,
      dataCenter,
      rememberEntities,
      journalPluginId,
      snapshotPluginId,
      passivationStrategySettings,
      shardRegionQueryTimeout,
      stateStoreMode,
      rememberEntitiesStoreMode,
      tuningParameters,
      coordinatorSingletonSettings,
      leaseSettings)
}
