/*
 * $Id: V3Poller.java,v 1.130 2013/01/06 02:55:20 tlipkis Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.poller.v3;

import java.io.*;
import java.net.MalformedURLException;
import java.security.*;
import java.util.*;
import org.apache.commons.collections.*;
import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.crawler.*;
import org.lockss.daemon.*;
import org.lockss.hasher.*;
import org.lockss.plugin.*;
import org.lockss.plugin.definable.DefinablePlugin;
import org.lockss.poller.*;
import org.lockss.poller.PollManager.EventCtr;
import org.lockss.poller.v3.V3Serializer.*;
import org.lockss.protocol.*;
import org.lockss.protocol.psm.*;
import org.lockss.protocol.V3LcapMessage.PollNak;
import org.lockss.repository.RepositoryNode;
import org.lockss.scheduler.*;
import org.lockss.scheduler.Schedule.*;
import org.lockss.state.*;
import org.lockss.util.*;

/**
 * <p>The caller of a V3 Poll.  This class is responsible for inviting
 * participants into a poll, tallying their votes, and taking action based
 * on the result.</p>
 */
public class V3Poller extends BasePoll {

  // Status strings used by the peers.
  public static final int PEER_STATUS_INITIALIZED = 0;
  public static final int PEER_STATUS_WAITING_POLL_ACK = 1;
  public static final int PEER_STATUS_ACCEPTED_POLL = 2;
  public static final int PEER_STATUS_NOMINATED = 3;
  public static final int PEER_STATUS_WAITING_VOTE = 4;
  public static final int PEER_STATUS_VOTED = 5;
  public static final int PEER_STATUS_COMPLETE = 6;
  public static final int PEER_STATUS_ERROR = 7;
  public static final int PEER_STATUS_DROPPED_OUT = 8;
  public static final int PEER_STATUS_DECLINED_POLL = 9;
  public static final int PEER_STATUS_NO_TIME = 10;
  public static final int PEER_STATUS_NO_VOTE = 11;
  public static final int PEER_STATUS_NO_RESPONSE = 12;
  public static final int PEER_STATUS_NO_NOMINATIONS = 13;

  public static final String[] PEER_STATUS_STRINGS =
  {
   "Initialized", "Invited", "Accepted Poll", "Sent Nominees",
   "Waiting for Vote", "Voted", "Complete", "Error", "Dropped Out",
   "Declined Poll", "No Time Available", "Didn't Vote", "No Response",
   "No Nominations",
  };

  public static final int POLLER_STATUS_STARTING = 0;
  public static final int POLLER_STATUS_NO_TIME = 1;
  public static final int POLLER_STATUS_RESUMING = 2;
  public static final int POLLER_STATUS_INVITING_PEERS = 3;
  public static final int POLLER_STATUS_HASHING = 4;
  public static final int POLLER_STATUS_TALLYING = 5;
  public static final int POLLER_STATUS_COMPLETE = 6;
  public static final int POLLER_STATUS_NO_QUORUM = 7;
  // Some error occured
  public static final int POLLER_STATUS_ERROR = 8;
  // The poll expired while it was hibernated
  public static final int POLLER_STATUS_EXPIRED = 9;
  public static final int POLLER_STATUS_WAITING_REPAIRS = 10;
  public static final int POLLER_STATUS_ABORTED = 11;

  public static final String[] POLLER_STATUS_STRINGS =
  {
   "Starting", "No Time Available", "Resuming", "Inviting Peers", "Hashing",
   "Tallying", "Complete", "No Quorum", "Error", "Expired", 
   "Waiting for Repairs", "Aborted",
  };

  private static final String PREFIX = Configuration.PREFIX + "poll.v3.";

  /** Quorum for V3 polls. */
  public static final String PARAM_QUORUM = PREFIX + "quorum";
  public static final int DEFAULT_QUORUM = 5;

  /** Target size of the outer circle poll. */
  public static final String PARAM_TARGET_OUTER_CIRCLE_SIZE =
    PREFIX + "targetOuterCircle";
  public static final int DEFAULT_TARGET_OUTER_CIRCLE_SIZE = 10;
  
  /** The maximum allowable number of simultaneous V3 Pollers */
  public static final String PARAM_MAX_SIMULTANEOUS_V3_POLLERS =
    PREFIX + "maxSimultaneousV3Pollers";
  public static final int DEFAULT_MAX_SIMULTANEOUS_V3_POLLERS = 10;

  /** If true, drop participants from this poll that do not send
   * outer circle nominees. */
  public static final String PARAM_DROP_EMPTY_NOMINATIONS =
    PREFIX + "dropEmptyNominations";
  public static final boolean DEFAULT_DROP_EMPTY_NOMINATIONS = false;
  
  /** If true, keep inviting peers into the poll until either enough
   * participants have agreed to participate, OR the vote timer expires,
   * whichever comes first.
   */
  public static final String PARAM_ENABLE_INVITATIONS =
    PREFIX + "enableFollowupInvitations";
  public static final boolean DEFAULT_ENABLE_INVITATIONS = true;
  
  /** Curve expressing decreasing weight of inviting peer who has
   * been unresponsive for X time.
   * @see org.lockss.util.CompoundLinearSlope */
  public static final String PARAM_INVITATION_WEIGHT_AGE_CURVE =
    PREFIX + "invitationWeightAgeCurve";
  public static final String DEFAULT_INVITATION_WEIGHT_AGE_CURVE =
    "[4d,1.0],[20d,0.1],[40d,0.01]";

  public static final String PARAM_INVITATION_WEIGHT_AT_RISK =
    PREFIX + "invitationWeightAtRisk";
  public static final float DEFAULT_INVITATION_WEIGHT_AT_RISK = 1.0f;

  public static final String PARAM_INVITATION_WEIGHT_ALREADY_REPAIRABLE =
    PREFIX + "invitationWeightSafety";
  public static final float DEFAULT_INVITATION_WEIGHT_ALREADY_REPAIRABLE =
    0.5f;

//   /** Curve expressing decreasing weight of inviting peer who has
//    * fewer than N willing repairers for AU
//    * @see org.lockss.util.CompoundLinearSlope */
//   public static final String PARAM_INVITATION_WEIGHT_SAFETY_CURVE =
//     PREFIX + "invitationWeightSafetyCurve";
//   public static final String DEFAULT_INVITATION_WEIGHT_SAFETY_CURVE =
//     null;

  /** List of [AUID,PeerId1,...,PeerIdN],... */
  public static final String PARAM_AT_RISK_AU_INSTANCES =
    PREFIX + "atRiskAuInstances";

  /** The time to wait between inviting more peers, until at least
   * MIN_POLL_SIZE have agreed to participate.
   */
  public static final String PARAM_TIME_BETWEEN_INVITATIONS =
    PREFIX + "timeBetweenInvitations";
  /** By default, wait two minutes between invitation messages and 
   * re-invitations.  This is sufficiently long for a voter to receive
   * and respond to an invitation if it has the AU and is willing to
   * participate.
   */
  public static final long DEFAULT_TIME_BETWEEN_INVITATIONS =
    2 * Constants.MINUTE;
  
  /** Subnet(s) not to invite into polls */
  public static final String PARAM_NO_INVITATION_SUBNETS =
    PREFIX + "noInvitationSubnets";

  /** The target number of participants is the quorum times this, rounded up
   */
  public static final String PARAM_TARGET_SIZE_QUORUM_MULTIPLIER =
    PREFIX + "targetSizeQuorumMultiplier";
  public static final double DEFAULT_TARGET_SIZE_QUORUM_MULTIPLIER = 1.25f;
  
  /**  number of invitations sent is this factor times the target size
   */
  public static final String PARAM_INVITATION_SIZE_TARGET_MULTIPLIER =
    PREFIX + "invitationSizeTargetMultiplier";
  public static final double DEFAULT_INVITATION_SIZE_TARGET_MULTIPLIER = 1.5f;

  /** If true, enable the discovery mechanism that attempts to invite peers
   * from outside our Initial Peer List into polls. */
  public static final String PARAM_ENABLE_DISCOVERY =
    PREFIX + "enableDiscovery";
  public static final boolean DEFAULT_ENABLE_DISCOVERY = true;

  /** If false, just log a message rather than deleting files that are
   * considered to be missing from a majority of peers.
   */
  public static final String PARAM_DELETE_EXTRA_FILES =
    PREFIX + "deleteExtraFiles";
  public static final boolean DEFAULT_DELETE_EXTRA_FILES = false;
  
  /**
   * Relative path to v3 state dir (from 1st element of diskSpacePaths)
   */
  public static final String PARAM_REL_STATE_PATH =
    PREFIX + "relStatePath";
  public static final String DEFAULT_REL_STATE_PATH =
    "v3state";

  /**
   * Absolute path to v3 state dir (takes precedence over
   * PARAM_REL_STATE_PATH)
   */
  public static final String PARAM_STATE_PATH =
    PREFIX + "statePath";

  /** The maximum allowable duration for a V3 poll */
  public static final String PARAM_MAX_POLL_DURATION =
    PREFIX + "maxPollDuration";
  public static long DEFAULT_MAX_POLL_DURATION = 3 * Constants.WEEK;
  
  /** The minimum allowable duration for a V3 poll */
  public static final String PARAM_MIN_POLL_DURATION =
    PREFIX + "minPollDuration";
  public static long DEFAULT_MIN_POLL_DURATION = 10 * Constants.MINUTE;

  /**
   * Multiplier to apply to the estimated hash duration to produce target
   * vote duration
   */
  public static final String PARAM_VOTE_DURATION_MULTIPLIER =
    PREFIX + "voteDurationMultiplier";
  public static final int DEFAULT_VOTE_DURATION_MULTIPLIER = 4;

  /**
   * Padding to add to target vote duration
   */
  public static final String PARAM_VOTE_DURATION_PADDING =
    PREFIX + "voteDurationPadding";
  public static final long DEFAULT_VOTE_DURATION_PADDING =
    5 * Constants.MINUTE;

  /*
   * Multiplier to apply to the estimated hash duration to produce target
   * tally duration
   */
  public static final String PARAM_TALLY_DURATION_MULTIPLIER =
    PREFIX + "tallyDurationMultiplier";
  public static final int DEFAULT_TALLY_DURATION_MULTIPLIER = 5;

  /**
   * Padding to add to target tally duration
   */
  public static final String PARAM_TALLY_DURATION_PADDING =
    PREFIX + "tallyDurationPadding";
  public static final long DEFAULT_TALLY_DURATION_PADDING =
    5 * Constants.MINUTE;
  
  /**
   * Extra time to allow for vote receipts (and repairs)?
   */
  public static final String PARAM_RECEIPT_PADDING =
    PREFIX + "receiptPadding";
  public static final long DEFAULT_RECEIPT_PADDING =
    5 * Constants.MINUTE;
  
  /**
   * Factor by which to extend poll duration and try again to find
   * schedulable time
   */
  public static final String PARAM_POLL_EXTEND_MULTIPLIER =
    PREFIX + "pollExtendMultiplier";
  public static final double DEFAULT_POLL_EXTEND_MULTIPLIER = 2.0;

  /**
   * Max factor by which to extend poll duration
   */
  public static final String PARAM_MAX_POLL_EXTEND_MULTIPLIER =
    PREFIX + "maxPollExtendMultiplier";
  public static final int DEFAULT_MAX_POLL_EXTEND_MULTIPLIER = 10;

  public static final String PARAM_V3_TRUSTED_WEIGHT =
    PREFIX + "trustedWeight";
  public static final int DEFAULT_V3_TRUSTED_WEIGHT =
    350;

  public static final String PARAM_V3_VOTE_MARGIN =
    PREFIX + "voteMargin";
  public static final int DEFAULT_V3_VOTE_MARGIN =
    75;

  /** Define the maximum number of queued repair requests that will
   * be allowed for this poll.  Zero (0) means that no repairs are 
   * allowed.  Any value less than zero means unlimited.
   */
  public static final String PARAM_MAX_REPAIRS =  PREFIX + "maxRepairs";
  public static final int DEFAULT_MAX_REPAIRS = 1000;

  /** The maximum number of block errors that can be encountered
   * during the tally before the poll is aborted.
   */
  public static final String PARAM_MAX_BLOCK_ERROR_COUNT =
    PREFIX + "maxBlockErrorCount";
  public static final int DEFAULT_MAX_BLOCK_ERROR_COUNT = 10;

  /**
   * The amount of time, in ms, to hold the poll open past normal closing time
   * if we are waiting for pending repairs.
   */
  // CR: should keep stats on use of this to extend polls
  public static final String PARAM_V3_EXTRA_POLL_TIME =
    PREFIX + "extraPollTime";
  public static final long DEFAULT_V3_EXTRA_POLL_TIME = 
    20 * Constants.MINUTE;

  /**
   * The probability of requesting a repair from other caches.
   */
  public static final String PARAM_V3_REPAIR_FROM_CACHE_PERCENT =
    PREFIX + "repairFromCachePercent";

  /**
   * In the absence of other advice, set the probability to match the repair
   * crawler's default probability.
   */
  public static final double DEFAULT_V3_REPAIR_FROM_CACHE_PERCENT =
    CrawlManagerImpl.DEFAULT_REPAIR_FROM_CACHE_PERCENT;
  
  public static final String PARAM_V3_ENABLE_REPAIR_FROM_CACHE =
    PREFIX + "enableRepairFromCache";
  public static final boolean DEFAULT_V3_ENABLE_REPAIR_FROM_CACHE = true;
  
  public static final String PARAM_V3_REPAIR_FROM_PUBLISHER_WHEN_TOO_CLOSE =
    PREFIX + "repairFromPublisherWhenTooClose";
  public static final boolean DEFAULT_V3_REPAIR_FROM_PUBLISHER_WHEN_TOO_CLOSE =
    false;
  
  /**
   * The number of bytes to hash before saving poll status during hashing.
   */
  public static final String PARAM_V3_HASH_BYTES_BEFORE_CHECKPOINT =
    PREFIX + "hashBytesBeforeCheckpoint";
  public static final long DEFAULT_V3_HASH_BYTES_BEFORE_CHECKPOINT =
    100 * 1024 * 1024; // 100 MB

  /**
   * The number of bytes to hash before saving poll status during hashing.
   */
  public static final String PARAM_REPAIR_HASH_ALL_VERSIONS =
    PREFIX + "repairHashAllVersions";
  public static final boolean DEFAULT_REPAIR_HASH_ALL_VERSIONS = false;

  /**
   * If requested, log extra information about the number of hashed
   * versions which were unique.
   */
  public static final String PARAM_LOG_UNIQUE_VERSIONS = 
    PREFIX + "logUniqueVersions";
  public static final boolean DEFAULT_LOG_UNIQUE_VERSIONS = false;

  public static final String PARAM_V3_ENABLE_HASH_STATS =
    PREFIX + "enableHashStats";
  public static final boolean DEFAULT_V3_ENABLE_HASH_STATS = false;
  
  /** Length of poller and voter challenges */
  public static final int HASH_NONCE_LENGTH = 20;


  // Global state for the poll.
  private PollerStateBean pollerState;
  // The order of theParticipants is used in indexing into the Arrays
  // passed to BlockHasher, and so it is a LinkedHashMap.
  protected LinkedHashMap<PeerIdentity,ParticipantUserData> theParticipants =
    new LinkedHashMap();
  protected Map<PeerIdentity,ParticipantUserData> exParticipants = new HashMap();
  // Only set once theParticipants is frozen. Nothing enforces this,
  // which seems awkward.
  private UrlTallier urlTallier;
  private LockssDaemon theDaemon;
  private PollManager pollManager;
  private IdentityManager idManager;
  private V3PollerSerializer serializer;
  private boolean resumedPoll;
  private boolean activePoll = true;
  private boolean dropEmptyNominators = DEFAULT_DROP_EMPTY_NOMINATIONS;
  private boolean deleteExtraFiles = DEFAULT_DELETE_EXTRA_FILES;
  private File stateDir;
  // The length, in ms., to hold the poll open past normal closing if
  // a little extra poll time is required to wait for pending repairs. 
  private long extraPollTime = DEFAULT_V3_EXTRA_POLL_TIME;
  private int quorum;
  private int voteMargin;
  private int outerCircleTarget;
  private int maxRepairs = DEFAULT_MAX_REPAIRS;
  private long voteDeadlinePadding = DEFAULT_VOTE_DURATION_PADDING;
  private long hashBytesBeforeCheckpoint =
    DEFAULT_V3_HASH_BYTES_BEFORE_CHECKPOINT;

  private static Logger log = Logger.getLogger("V3Poller");
  private static LockssRandom theRandom = new LockssRandom();
  private int blockErrorCount = 0;	// CR: s.b. in PollerStateBean
  private int maxBlockErrorCount = DEFAULT_MAX_BLOCK_ERROR_COUNT;
  private boolean enableInvitations = DEFAULT_ENABLE_INVITATIONS;
  private long timeBetweenInvitations = DEFAULT_TIME_BETWEEN_INVITATIONS;
  private double targetSizeQuorumMultiplier =
    DEFAULT_TARGET_SIZE_QUORUM_MULTIPLIER;
  private double invitationSizeTargetMultiplier =
    DEFAULT_INVITATION_SIZE_TARGET_MULTIPLIER;

  private SchedulableTask task;
  private TimerQueue.Request invitationRequest;
  private TimerQueue.Request pollCompleteRequest;
  private TimerQueue.Request voteCompleteRequest;
  private Deadline voteTallyStart;
  private Deadline nextInvitationTime;
  private long bytesHashedSinceLastCheckpoint = 0;
  private long totalBytesHashed = 0;
  private long totalBytesRead = 0;
  private int voteDeadlineMultiplier = DEFAULT_VOTE_DURATION_MULTIPLIER;
  private boolean enableDiscovery = DEFAULT_ENABLE_DISCOVERY;
  private VoteTallyCallback voteTallyCallback;
  private boolean repairHashAllVersions = DEFAULT_REPAIR_HASH_ALL_VERSIONS;
  private boolean logUniqueVersions = DEFAULT_LOG_UNIQUE_VERSIONS;
  private boolean enableHashStats = DEFAULT_V3_ENABLE_HASH_STATS;

  private long tallyEnd;

  // Probability of repairing from another cache.  A number between
  // 0.0 and 1.0.
  // CR: not a percentage; fix name
  private double repairFromCache =
    V3Poller.DEFAULT_V3_REPAIR_FROM_CACHE_PERCENT;
  
  private boolean enableRepairFromCache =
    V3Poller.DEFAULT_V3_ENABLE_REPAIR_FROM_CACHE;

  private boolean repairFromPublisherWhenTooClose =
    V3Poller.DEFAULT_V3_REPAIR_FROM_PUBLISHER_WHEN_TOO_CLOSE;

  // CR: Factor out common elements of the two constructors
  /**
   * <p>Create a new Poller to call a V3 Poll.</p>
   * 
   * @param spec The PollSpec
   * @param daemon The LockssDaemon
   * @param orig The originator of the poll (i.e., my PeerIdentity)
   * @param key The key for the poll, generated by {@link V3PollFactory}
   * @param duration  The length of the poll.
   * @param hashAlg The hashing algorithm to use.
   */
  public V3Poller(PollSpec spec, LockssDaemon daemon, PeerIdentity orig,
                  String key, long duration, String hashAlg)
      throws V3Serializer.PollSerializerException {
    this.theDaemon = daemon;
    this.pollManager = daemon.getPollManager();
    this.idManager = daemon.getIdentityManager();

    setConfig();

    if (hashAlg == null) hashAlg = LcapMessage.DEFAULT_HASH_ALGORITHM;
    // If the hash algorithm is not available, fail the poll immediately.
    try {
      MessageDigest.getInstance(hashAlg);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalArgumentException("Algorithm " + hashAlg +
                                         " is not supported");
    }
    this.serializer = new V3PollerSerializer(theDaemon);
    long pollEnd = TimeBase.nowMs() + duration;

    pollerState = new PollerStateBean(spec, orig, key,
				      duration, pollEnd,
                                      outerCircleTarget,
                                      quorum, voteMargin,
				      hashAlg);

    long estimatedHashTime = getCachedUrlSet().estimatedHashDuration();

    // The vote deadline is the deadline by which all voters must have
    // voted.
    TimeInterval tallyWindow = PollUtil.calcV3TallyWindow(estimatedHashTime,
							  duration);
    // XXX account for message send time?
    long voteDeadline = tallyWindow.getBeginTime();

    pollerState.setVoteDeadline(voteDeadline);

    tallyEnd = tallyWindow.getEndTime();

    // Checkpoint the poll.
    checkpointPoll();
  }
  
  /**
   * <p>Restore a V3 Poll from a serialized state.</p>
   */
  public V3Poller(LockssDaemon daemon, File pollDir)
      throws V3Serializer.PollSerializerException {
    theDaemon = daemon;
    serializer = new V3PollerSerializer(theDaemon, pollDir);
    pollerState = serializer.loadPollerState();
    int oldState = pollerState.getStatus();
    setStatus(POLLER_STATUS_RESUMING);
    // If the hash algorithm used when the poll was first created is
    // no longer available, fail the poll immediately.
    try {
      MessageDigest.getInstance(pollerState.getHashAlgorithm());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalArgumentException("Algorithm " +
                                         pollerState.getHashAlgorithm() +
                                         " is no longer supported");
    }
    pollManager = daemon.getPollManager();
    idManager = daemon.getIdentityManager();
    setConfig();
    // Restore transient cus and pollspec in the poller state.
    PluginManager plugMgr = theDaemon.getPluginManager();
    CachedUrlSet cus = plugMgr.findCachedUrlSet(pollerState.getAuId());
    if (cus == null) {
      // CR: NoSuchAuException
      throw new NullPointerException("CUS for AU " + pollerState.getAuId() +
                                     " is null!");
    }
    pollerState.setCachedUrlSet(cus);
    pollerState.setPollSpec(new PollSpec(cus, Poll.V3_POLL));

    // Restore the peers for this poll.
    try {
      restoreParticipants();
    } catch (V3Serializer.PollSerializerException ex) {
      log.error("Unable to restore poll state!");
    }

    resumedPoll = true;
    setStatus(oldState);
    log.debug2("Restored serialized poll " + pollerState.getPollKey());
  }

  void setConfig() {
    Configuration c = ConfigManager.getCurrentConfig();
    outerCircleTarget = c.getInt(PARAM_TARGET_OUTER_CIRCLE_SIZE,
                                 DEFAULT_TARGET_OUTER_CIRCLE_SIZE);
    quorum = c.getInt(PARAM_QUORUM, DEFAULT_QUORUM);
    voteMargin = c.getInt(PARAM_V3_VOTE_MARGIN, DEFAULT_V3_VOTE_MARGIN);
    dropEmptyNominators = c.getBoolean(PARAM_DROP_EMPTY_NOMINATIONS,
                                       DEFAULT_DROP_EMPTY_NOMINATIONS);
    deleteExtraFiles = c.getBoolean(PARAM_DELETE_EXTRA_FILES,
                                    DEFAULT_DELETE_EXTRA_FILES);
    voteDeadlineMultiplier = c.getInt(PARAM_VOTE_DURATION_MULTIPLIER,
				      DEFAULT_VOTE_DURATION_MULTIPLIER);
    voteDeadlinePadding = c.getTimeInterval(PARAM_VOTE_DURATION_PADDING,
                                            DEFAULT_VOTE_DURATION_PADDING);
    timeBetweenInvitations =
      c.getTimeInterval(PARAM_TIME_BETWEEN_INVITATIONS,
                        DEFAULT_TIME_BETWEEN_INVITATIONS);
    targetSizeQuorumMultiplier =
      c.getDouble(PARAM_TARGET_SIZE_QUORUM_MULTIPLIER,
		 DEFAULT_TARGET_SIZE_QUORUM_MULTIPLIER);
    invitationSizeTargetMultiplier =
      c.getDouble(PARAM_INVITATION_SIZE_TARGET_MULTIPLIER,
		 DEFAULT_INVITATION_SIZE_TARGET_MULTIPLIER);
    extraPollTime = c.getTimeInterval(PARAM_V3_EXTRA_POLL_TIME,
				      DEFAULT_V3_EXTRA_POLL_TIME);
    enableDiscovery = c.getBoolean(PARAM_ENABLE_DISCOVERY,
                                   DEFAULT_ENABLE_DISCOVERY);
    hashBytesBeforeCheckpoint = 
      c.getLong(PARAM_V3_HASH_BYTES_BEFORE_CHECKPOINT,
                DEFAULT_V3_HASH_BYTES_BEFORE_CHECKPOINT);
    repairFromCache = 
      c.getPercentage(PARAM_V3_REPAIR_FROM_CACHE_PERCENT,
                      DEFAULT_V3_REPAIR_FROM_CACHE_PERCENT);
    maxRepairs = c.getInt(PARAM_MAX_REPAIRS, DEFAULT_MAX_REPAIRS);

    stateDir = PollUtil.ensurePollStateRoot();

    repairHashAllVersions = c.getBoolean(PARAM_REPAIR_HASH_ALL_VERSIONS,
					 DEFAULT_REPAIR_HASH_ALL_VERSIONS);
    enableRepairFromCache = c.getBoolean(PARAM_V3_ENABLE_REPAIR_FROM_CACHE,
					 DEFAULT_V3_ENABLE_REPAIR_FROM_CACHE);
    repairFromPublisherWhenTooClose =
      c.getBoolean(PARAM_V3_REPAIR_FROM_PUBLISHER_WHEN_TOO_CLOSE,
		   DEFAULT_V3_REPAIR_FROM_PUBLISHER_WHEN_TOO_CLOSE);
    logUniqueVersions = c.getBoolean(PARAM_LOG_UNIQUE_VERSIONS,
				     DEFAULT_LOG_UNIQUE_VERSIONS);
    enableHashStats = c.getBoolean(PARAM_V3_ENABLE_HASH_STATS,
				   DEFAULT_V3_ENABLE_HASH_STATS);
  }

  PsmInterp newPsmInterp(PsmMachine stateMachine, Object userData) {
    PsmManager mgr = theDaemon.getPsmManager();
    PsmInterp interp = mgr.newPsmInterp(stateMachine, userData);
    interp.setName(PollUtil.makeShortPollKey(getKey()));
    interp.setThreaded(true);
    return interp;
  }

  /**
   * <p>Reserve time in the schedule to tally the votes.</p>
   * 
   * @param maxParticipants  The maximum number of participants that will
   *        participate in this poll (invitees and outer circle)
   */
  boolean reserveScheduleTime(int maxParticipants) {
    CachedUrlSet cus = this.getCachedUrlSet();
    long hashEst = cus.estimatedHashDuration();
    long receiptBuffer = calculateReceiptBuffer();
    long now = TimeBase.nowMs();
    
    // Estimate of when the vote is over and we can start tallying
    Deadline earliestStart = Deadline.at(pollerState.getVoteDeadline());
    Deadline latestFinish =
      Deadline.at(getDeadline().getExpirationTime() - receiptBuffer);

    TaskCallback tc = new TaskCallback() {
      public void taskEvent(SchedulableTask task, EventType type) {
        // do nothing... yet!
      }
    };
    this.task = new StepTask(earliestStart, latestFinish,
                             hashEst, tc, this) {
      public int step(int n) {
	// finish immediately, in case we start running
	setFinished();
        return n;
      }
    };
    boolean suc = theDaemon.getSchedService().scheduleTask(task);
    if (!suc) {
      String msg = "No room in schedule for" + 
	StringUtil.timeIntervalToString(hashEst) + " hash between " +
	earliestStart + " and " + latestFinish + ", at " + TimeBase.nowDate();
      pollerState.setErrorDetail(msg);
      log.warning(msg);
    }

    return suc;
  }
  
  private long calculateReceiptBuffer() {
    return
      CurrentConfig.getTimeIntervalParam(PARAM_RECEIPT_PADDING,
					 DEFAULT_RECEIPT_PADDING);
  }

  /**
   * Restore the set of serialized pariticipants when restoring a poll.
   *
   * @throws V3Serializer.PollSerializerException
   */
  protected void restoreParticipants()
      throws V3Serializer.PollSerializerException {
    synchronized (theParticipants) {
      Collection peers = serializer.loadVoterStates();
      for (Iterator iter = peers.iterator(); iter.hasNext();) {
	final ParticipantUserData voterState =
	  (ParticipantUserData)iter.next();
	PeerIdentity id = voterState.getVoterId();
	PsmInterpStateBean pisb = voterState.getPsmInterpState();
	PsmMachine machine = makeMachine();
	PsmInterp interp = newPsmInterp(machine, voterState);
	voterState.setPsmInterpState(pisb);
	interp.setCheckpointer(new PsmInterp.Checkpointer() {
	    public void checkpoint(PsmInterpStateBean resumeStateBean) {
	      // Replace the statebean in this machine's state object.
	      voterState.setPsmInterpState(resumeStateBean);
	      try {
		checkpointParticipant(voterState);
	      } catch (PollSerializerException ex) {
		log.error("Unable to save participant state for peer " +
			  voterState.getVoterId());
	      }
	    }
	  });
	voterState.setPsmInterp(interp);
	voterState.setPollState(pollerState);
	voterState.setPoller(this);
	theParticipants.put(id, voterState);
      }
    }
  }

  PsmInterp.ErrorHandler ehAbortPoll(final String msg) {
    return new PsmInterp.ErrorHandler() {
	public void handleError(PsmException e) {
	  log.warning(msg, e);
	  stopPoll(POLLER_STATUS_ERROR);
	}
      };
  }
	  
  PsmInterp.ErrorHandler ehRemoveParticipant(final PeerIdentity id,
						       final String msg) {
    return new PsmInterp.ErrorHandler() {
	public void handleError(PsmException e) {
	  log.warning(msg, e);
	  removeParticipant(id);
	}
      };
  }

  PsmInterp.ErrorHandler ehIgnore(final String msg) {
    return new PsmInterp.ErrorHandler() {
	public void handleError(PsmException e) {
	  log.warning(msg, e);
	}
      };
  }
	  
  /**
   * Start a poll.  Overrides BasePoll.startPoll().
   */
  public void startPoll() {
    pollManager.countEvent(EventCtr.Polls);
    if (!resumedPoll) {
      // Construct the initial inner circle only once
      constructInnerCircle(pollerState.getQuorum());
      setStatus(V3Poller.POLLER_STATUS_INVITING_PEERS);
    }
    
    voteTallyCallback = new VoteTallyCallback();

    log.debug("Scheduling V3 poll " + pollerState.getPollKey() +
              " to complete by " +
	      Deadline.restoreDeadlineAt(pollerState.getPollDeadline()));

    Deadline pollDeadline = null;

    if (resumedPoll) {
      // Bypass sanity check.
      voteTallyStart =
	Deadline.restoreDeadlineAt(pollerState.getVoteDeadline());
      pollDeadline = Deadline.restoreDeadlineAt(pollerState.getPollDeadline());
    } else {
      // Sanity check
      voteTallyStart = Deadline.at(pollerState.getVoteDeadline());
      pollDeadline = Deadline.at(pollerState.getPollDeadline());
    }
    tallyEnd = pollerState.getPollDeadline() - calculateReceiptBuffer();

    // Schedule the vote tally callback.  The poll will tally votes no earlier
    // than this deadline.
    if ((voteTallyStart.expired() &&
        pollerState.votedPeerCount() < pollerState.getQuorum()) ||
        pollDeadline.expired()) {
      String msg = "Poll expired before tallying could complete";
      log.warning(msg + ": " + pollerState.getPollKey());
      pollerState.setErrorDetail(msg);
      stopPoll(V3Poller.POLLER_STATUS_EXPIRED);
      return;
    } else {
      voteCompleteRequest =
        TimerQueue.schedule(voteTallyStart, voteTallyCallback, this);
    }

    if (reserveScheduleTime(getPollSize())) {
      log.debug("Scheduled time for a new poll with a requested poll size of "
                + getPollSize());

      // Schedule the poll deadline.  The poll must complete by this time.
      pollCompleteRequest =
        TimerQueue.schedule(pollDeadline,
                            new PollCompleteCallback(), this);

      // Sanity check.  This really *should not* ever happen.
      if (pollerState.getVoteDeadline() >= getDeadline().getExpirationTime()) {
        log.warning("Vote deadline (" + pollerState.getVoteDeadline() + 
                    ") is later than poll deadline (" + getDeadline() +
                    ") in poll " + getKey());
        stopPoll(POLLER_STATUS_EXPIRED);
        return;
      }

      // XXX Should be in PollManager
      if (!resumedPoll) {
	AuState auState = theDaemon.getNodeManager(getAu()).getAuState();
	auState.pollStarted();
      }
    } else {
      log.warning("Unable to schedule time for poll " + getKey());
      stopPoll(POLLER_STATUS_NO_TIME);
      return;
    }

    synchronized(theParticipants) {
      for (ParticipantUserData ud : theParticipants.values()) {
        if (!ud.isOuterCircle()) { // Start polling only inner circle members.
	  startParticipant(ud, resumedPoll);
        }
      }
    }
    
    if (enableInvitations) {
      // Set up an event on the timer queue to check for accepted peers.
      // If we haven't got enough peers, invite more.
      log.debug("Scheduling check for more peers to invite in " +
                StringUtil.timeIntervalToString(timeBetweenInvitations));
      nextInvitationTime = Deadline.in(timeBetweenInvitations);
      invitationRequest = 
        TimerQueue.schedule(nextInvitationTime,
                            new InvitationCallback(), this);
    } else {
      log.debug("Not scheduling a followup invitation check, " +
                "due to configuration.");
    }
  }

  void startParticipant(ParticipantUserData ud, boolean resume) {
    PeerIdentity id = ud.getVoterId();
    PsmInterp interp = ud.getPsmInterp();
    try {
      if (resume) {
	String msg = "Error resuming voter";
	interp.enqueueResume(ud.getPsmInterpState(),
			     ehRemoveParticipant(id, msg));
      } else {
	String msg = "Error starting voter";
	interp.enqueueStart(ehRemoveParticipant(id, msg));
      }
    } catch (PsmException e) {
      log.warning("State machine error", e);
      removeParticipant(id);
      // 	      stopPoll(POLLER_STATUS_ERROR);
    }
  }

  /**
   * Stop the poll, and set the supplied status.
   */
  public void stopPoll(final int status) {
    pollManager.countPollEndEvent(status);
    // Force the poll to be complete, and continue only if it was
    // previously not complete, to ensure that the rest of this method
    // is executed only once.
    if (!checkAndCompletePoll()) {
      log.debug("Poll has already been closed: " + getKey());
      return;
    }

    log.info("Stopping poll " + getKey() + " with status " + 
             V3Poller.POLLER_STATUS_STRINGS[status]);
    setStatus(status);
    AuState auState = theDaemon.getNodeManager(getAu()).getAuState();
    auState.pollFinished(status);
    if (task != null && !task.isExpired()) {
      log.debug2("Cancelling task");
      task.cancel();
    }
    if (invitationRequest != null) {
      log.debug2("Cancelling invitation request timer event.");
      TimerQueue.cancel(invitationRequest);
    }
    if (voteCompleteRequest != null) {
      log.debug2("Cancelling vote completion timer event.");
      TimerQueue.cancel(voteCompleteRequest);
    }
    if (pollCompleteRequest != null) {
      log.debug2("Cancelling poll completion timer event.");
      TimerQueue.cancel(pollCompleteRequest);
    }

    long now = TimeBase.nowMs();
    pollerState.setPollEnd(now);
    // Reset the duration to reflect reality
    pollerState.setDuration(now - pollerState.getCreateTime());

    if (urlTallier != null) {
      urlTallier.release();
      urlTallier = null;
    }

    // Clean up any lingering participants.
    synchronized(theParticipants) {
      for (ParticipantUserData ud : theParticipants.values()) {
        VoteBlocks voteBlocks = ud.getVoteBlocks();
        if (voteBlocks != null) {
          voteBlocks.release();
        }
      }
    }
    serializer.closePoll();
    pollManager.closeThePoll(pollerState.getPollKey());      
    
    log.debug("Closed poll " + pollerState.getPollKey());
    
    // Finally, release unneeded resources
    release();
  }

  /**
   * <p>Stop the poll.  Overrides BasePoll.stopPoll().</p>
   *
   */
  public void stopPoll() {
    stopPoll(POLLER_STATUS_COMPLETE);
  }

  public void abortPoll() {
    stopPoll(POLLER_STATUS_ABORTED);
  }

  /**
   * <p>Examine the nominees from each peer. Choose a fixed number
   * to allow from each peer.  If any peer has sent no nominees,
   * remove it from the poll.</p>
   */
  private void pollOuterCircle() {
    synchronized(theParticipants) {
      // Calculate the correct number of invitees to choose from each peer's
      // invitee list.
      int pollSize = getPollSize();
      int targetSize =
	(int)((pollerState.getOuterCircleTarget() + pollSize - 1)
	      / pollSize);
      log.debug2("Target for nominees from each inner circle participant: " +
		 targetSize);
      Collection outerCircle = constructOuterCircle(targetSize);
      // Now start polling the outer circle.
      for (Iterator it = outerCircle.iterator(); it.hasNext(); ) {
        String idStr = (String) it.next();
	try {
	  PeerIdentity id = idManager.findPeerIdentity(idStr);
	  log.debug2("Adding new peer " + id + " to the outer circle");
	  ParticipantUserData participant = makeParticipant(id);
	  participant.isOuterCircle(true);
	  theParticipants.put(id, participant);
	  startParticipant(participant, false);
	} catch (IdentityManager.MalformedIdentityKeyException e) {
	  log.warning("Can't add to outer circle: " + idStr, e);
	}
      }
    }
  }

  protected Collection constructOuterCircle(int target) {
    Collection outerCircle = new ArrayList();
    // Attempt to randomly select 'target' peers from each voter's
    // nominee list.  If there are not enough peers, just nominate whoever
    // we can.
    synchronized(theParticipants) {
      for (ParticipantUserData participant : theParticipants.values()) {
        if (participant.getNominees() == null)
          continue;
        
        List<String> nominees = new ArrayList();
        
        // Reject any nominees we should not include.
        for (String pidKey : (List<String>)participant.getNominees()) {
	  try {
	    if (isPeerEligible(pidKey)) {
	      nominees.add(pidKey);
	    }
	  } catch (IdentityManager.MalformedIdentityKeyException e) {
	    log.warning("Bad nomination: " + pidKey + " from " +
			participant.getVoterId(), e);
          }
        }

        if (nominees == null || nominees.size() == 0) {
          // If we want to drop peers that don't send any nominees,
          // they will have already been removed at this point,
          // so just log a debug statement and go on.
          log.debug2("Peer " + participant.getVoterId()
                     + " did not nominate anyone we can include.");
          continue;
        } else if (nominees.size() < target) {
          log.warning("Peer " + participant.getVoterId() +
                      " only sent " + nominees.size() + " nominations.");
          outerCircle.addAll(nominees);
          continue;
        } else {
          log.debug3("Randomly selecting " + target + " nominees from " +
                     "the set " + nominees);
          outerCircle.addAll(CollectionUtil.randomSelection(nominees, target));
        }
      }
    }
    return outerCircle;
  }

  /**
   * Add a voter to the inner circle of the poll.
   *
   * @param id
   * @return PsmInterp
   */
  ParticipantUserData addInnerCircleVoter(PeerIdentity id) {
    ParticipantUserData participant = makeParticipant(id);
    synchronized(theParticipants) {
      theParticipants.put(id, participant);
    }
    return participant;
  }

  /**
   * Create a new ParticipantUserData state object for the specified peer.
   *
   * @return ParticipantUserData for the specified peer
   */
  private ParticipantUserData makeParticipant(final PeerIdentity id) {
    final ParticipantUserData participant =
      new ParticipantUserData(id, this, stateDir);
    participant.setPollerNonce(PollUtil.makeHashNonce(HASH_NONCE_LENGTH));
    PsmMachine machine = makeMachine();
    PsmInterp interp = newPsmInterp(machine, participant);
    interp.setCheckpointer(new PsmInterp.Checkpointer() {
      public void checkpoint(PsmInterpStateBean resumeStateBean) {
        // Replace the statebean in this machine's state object.
        participant.setPsmInterpState(resumeStateBean);
        try {
          checkpointParticipant(participant);
        } catch (PollSerializerException ex) {
          log.error("Unable to save participant state for peer " +
                    participant.getVoterId());
        }
      }
    });
    participant.setPsmInterp(interp);
    return participant;
  }

  /**
   * Called by a participant's state machine when it receives a set of nominees
   * from a voter.
   *
   * @param id The PeerIdentity of the participant sending the nominees.
   * @param nominatedPeers A list of string representations of PeerIdendities
   *          the peer is nominating.
   */
  void nominatePeers(PeerIdentity id, List nominatedPeers) {
    // Only honor nominations if this is an inner circle peer.
    ParticipantUserData peer = getParticipant(id);
    if (peer.isOuterCircle()) {
      log.debug2("Ignoring nominations from outer circle voter " + id);
      return;
    }
    // Peers should never be allowed to nominate themsevles.
    if (nominatedPeers != null) {
      nominatedPeers.remove(id);
    }
    log.debug2("Received nominations from inner circle voter: " + id +
               "; Nominations = " + nominatedPeers);
    // If the peer has sent us no nominations, decide whether to drop him.
    if (dropEmptyNominators &&
        (nominatedPeers == null || nominatedPeers.size() == 0)) {
      log.warning("Peer " + id + " did not nominate anyone.  Removing from " +
                  "poll.");
      removeParticipant(id, PEER_STATUS_NO_NOMINATIONS);
      return;
    }
    // Store the nominees in the participant's userdata
    peer.setNominees(nominatedPeers);
    pollerState.signalVoterNominated(id);
    checkpointPoll();
    // XXX Probably shouldn't start outer circle in participant state
    // machine thread.
    if (pollerState.sufficientPeers()) {
      pollOuterCircle();
    }
  }

  /**
   * Compare the plain hash of two versions.
   */
  static final Comparator<HashBlock.Version> plainHashComparator =
    new Comparator<HashBlock.Version>() {
      public int compare(HashBlock.Version o1, HashBlock.Version o2) {
	byte[] lastHash1 = getPlainHash(o1.getHashes());
	byte[] lastHash2 = getPlainHash(o2.getHashes());
	return ByteArray.lexicographicalCompare(lastHash1, lastHash2);
      }
  };

  /**
   * Tally a hash block which the poller has, and all the blocks any
   * voter has before that.
   *
   * @param hashBlock  The {@link HashBlock} to tally.
   * @return The {@link BlockTally} with the final tally results. The
   * results have been acted upon, and the tally is returned for
   * testing purposes.
   */
  BlockTally tallyBlock(HashBlock hashBlock) {
    setStatus(V3Poller.POLLER_STATUS_TALLYING);

    final String pollerUrl = hashBlock.getUrl();
    log.debug3("Opening block " + pollerUrl + " to tally.");
    if (logUniqueVersions) {
      int uniqueSize = hashBlock.countUniqueVersions(plainHashComparator);
      int size = hashBlock.size();
      if (size == uniqueSize) {
	log.debug("Hashed " + size + " versions; all unique: " + pollerUrl );
      } else {
	log.debug("Hashed " + size + " versions; " + uniqueSize + " unique: "
		  + pollerUrl);
      }
    }

    totalBytesHashed += hashBlock.getTotalHashedBytes();
    totalBytesRead += hashBlock.getTotalUnfilteredBytes();

    tallyVoterUrls(pollerUrl);
    BlockTally tally = tallyPollerUrl(pollerUrl, hashBlock);

    // Check to see if it's time to checkpoint the poll.
    bytesHashedSinceLastCheckpoint += hashBlock.getTotalHashedBytes();
    if (bytesHashedSinceLastCheckpoint >= this.hashBytesBeforeCheckpoint) {
      checkpointPoll();
    }
    return tally;
  }

  /**
   * After the poller has no more blocks, process all the blocks any
   * voter has.
   */
  private void finishTally() {
    if (!hasQuorum()) {
      log.error("finishTally() called in inquorate poll");
      return;
    }
    tallyVoterUrls();
    
    // Checkpoint the poll.
    checkpointPoll();

    // Do any pending repairs.
    doRepairs();
  }

  /**
   * Tally and consume all the blocks we are missing, up to but not
   * including pollerUrl.
   */
  private void tallyVoterUrls(String pollerUrl) {
    log.debug3("tallyVoterUrls: "+pollerUrl);
    while (true) {
      String voterUrl = urlTallier.peekUrl();
      log.debug3("voters have: "+voterUrl);
      if (voterUrl == null || voterUrl.compareTo(pollerUrl) >= 0) {
	// Leave the while loop, since the voter was greater than the
	// pollerUrl.
	break;
      }
      tallyVoterUrl(voterUrl);
    }
  }
  
  /**
   * Tally and consume all the remaining blocks.
   */
  private void tallyVoterUrls() {
    while (true) {
      String voterUrl = urlTallier.peekUrl();
      if (voterUrl == null) {
	// Leave the while loop, since the voters have nothing left.
	break;
      }
      tallyVoterUrl(voterUrl);
    }
  }

  /**
   * Tally all the voters who have this block which the poller does
   * not, consuming the block in the voters.
   */
  private void tallyVoterUrl(String voterUrl) {
    log.debug3("tallyVoterUrl: "+voterUrl);
    BlockTally tally = urlTallier.tallyVoterUrl(voterUrl);
    checkTally(tally, voterUrl, true);
  }

  /**
   * Tally all the voters who have this block which the poller also
   * has, consuming the block in the voters.
   * @param pollerUrl The URL being tallied.
   * @param hashBlock The poller's hashes for the URL.
   * @return The {@link BlockTally} with the final tally results. The
   * results have been acted upon, and the tally is returned for
   * testing purposes.
   */
  private BlockTally tallyPollerUrl(String pollerUrl, HashBlock hashBlock) {
    log.debug3("tallyPollerUrl: "+pollerUrl);
    BlockTally tally = urlTallier.tallyPollerUrl(pollerUrl, hashBlock);
    signalNodeAgreement(tally, pollerUrl);
    checkTally(tally, pollerUrl, true);
    return tally;
  }

  private Collection<PeerIdentity>
      getVotersIdentities(Collection<ParticipantUserData> voters) {
    ArrayList<PeerIdentity> ids = new ArrayList(voters.size());
    for (ParticipantUserData voter: voters) {
      ids.add(voter.getVoterId());
    }
    return ids;
  }

  /**
   * <p>Update the node agreement history for the URL.</p>
   *
   * @param tally The tally containing votes.
   * @param url The target URL.
   */
  private void signalNodeAgreement(BlockTally tally, String url) {
    if (tally.getAgreeVoters().size() > 0) {
      try {
        RepositoryNode node = AuUtil.getRepositoryNode(getAu(), url);
        if (node == null) {
	  // CR: throw new ShouldNotHappenException();
	} else {
	  Collection<PeerIdentity> agreeVoterIds = 
	    getVotersIdentities(tally.getAgreeVoters());
          node.signalAgreement(agreeVoterIds);
	}
      } catch (MalformedURLException ex) {
        log.error("Malformed URL while updating agreement history: " 
                  + url);
      }
    }
  }

  /**
   * <p>Check the tally for a block, and perform repairs if necessary.</p>
   *
   * @param tally The tally to check.
   * @param url The target URL for any possible repairs.
   * @return The status of the tally.
   */
  private BlockTally.Result checkTally(BlockTally tally, String url,
				       boolean repairIfNecessary) {
    // Should never happen -- if it does, track it down.
    if (url == null) {
      throw new NullPointerException("Passed a null url to checkTally!");
    }
    
    setStatus(V3Poller.POLLER_STATUS_TALLYING);
    BlockTally.Result tallyResult =
      tally.getTallyResult(pollerState.getQuorum(),
			   pollerState.getVoteMargin());
    PollerStateBean.TallyStatus tallyStatus = pollerState.getTallyStatus();

    // Have now counted agreement in tally and recorded per-file agreement.
    // Check tally only for quorate polls
    if (hasQuorum()) {

      // Linked hash map - order is significant
      String pollKey = pollerState.getPollKey();
      String vMsg = "";
      if (log.isDebug2()) {
	vMsg = tally.votes();
      }
      switch (tallyResult) {
      case WON:
	tallyStatus.addAgreedUrl(url);
	// Great, we won!  Do nothing.
	log.debug3("Won tally" + vMsg + ": " + url + " in poll " + pollKey);
	break;
      case LOST:
	tallyStatus.addDisagreedUrl(url);
	log.debug2("Lost tally" + vMsg + ": " + url + " in poll " + getKey());
	if (repairIfNecessary) {
	  requestRepair(url, tally.getDisagreeVoters());
	}
	break;
      case LOST_POLLER_ONLY_BLOCK:
	log.debug2("Lost tally" + vMsg + ": Removing " + url +
		   " in poll " + getKey());
	deleteBlock(url);
	break;
      case LOST_VOTER_ONLY_BLOCK:
	tallyStatus.addDisagreedUrl(url);
	if (repairIfNecessary) {
	  // shouldn't happen
	  log.debug2("Lost tally. Requesting repair for missing block: " +
		     url + " in poll " + getKey());
	  requestRepair(url, tally.getVoterOnlyBlockVoters());
	}
	break;
      case NOQUORUM:
	tallyStatus.addNoQuorumUrl(url);
	log.warning("No Quorum for block " + url + " in poll " + getKey());
	break;
      case TOO_CLOSE:
	tallyStatus.addTooCloseUrl(url);
	log.warning("Tally was inconclusive for block " + url + " in poll " +
		    getKey());
	if (repairIfNecessary &&
	    AuUtil.isRepairFromPublisherWhenTooClose(getAu(),
						     repairFromPublisherWhenTooClose)) {
	  requestRepairFromPublisher(url);
	}
	break;
      default:
	log.warning("Unexpected results from tallying block " + url + ": "
		    + tallyResult.printString);
      }
    }
    return tallyResult;
  }

  /**
   * <p>Callback method called by each PollerStateMachine when entering the
   * TallyVoter state.</p>
   */
  boolean tallyVoter(PeerIdentity id) {
    pollerState.addVotedPeer(id);
    checkpointPoll();
    
    // A shortcut 
    if (pollerState.votedPeerCount() == getPollSize()) {
      log.debug("All invited peers have voted.  Rescheduling vote deadline.");

      voteTallyStart.expire();
    }
    
    return true;
  }

  /**
   * Schedule hashing of the AU.
   *
   * @return true iff the hash is scheduled successfully.
   */
  protected boolean scheduleHash(CachedUrlSet cu,
				 Deadline deadline,
                                 HashService.Callback cb,
                                 BlockHasher.EventHandler eh) {
    return scheduleHash(cu, -1, deadline, cb, eh);
  }

  protected boolean scheduleHash(CachedUrlSet cu,
				 int maxVersions,
				 Deadline deadline,
                                 HashService.Callback cb,
                                 BlockHasher.EventHandler eh) {
    log.debug("Scheduling " + cu + "(" + maxVersions + ") hash for poll "
	      + pollerState.getPollKey());
    BlockHasher hasher = new BlockHasher(cu,
					 maxVersions,
                                         initHasherDigests(),
                                         initHasherByteArrays(),
                                         eh);
    // Now schedule the hash
    HashService hashService = theDaemon.getHashService();
    
    boolean res = false;
    int oldStatus = getStatus();
    try {
      setStatus(POLLER_STATUS_HASHING);
      res = hashService.scheduleHash(hasher, deadline, cb, null);
      if (!res) {
	setStatus(oldStatus);
      }
    } catch (Exception e) {
      setStatus(oldStatus);
    }
    return res;
  }

  /**
   * @return the plain hash.
   */
  static byte[] getPlainHash(byte[][] hasherByteArrays) {
    return hasherByteArrays[hasherByteArrays.length-1];
  }

  /**
   * Create an array of byte arrays containing hasher initializer
   * bytes, one for each participant in the poll, and one for a plain
   * hash. The initializer bytes are constructed by concatenating the
   * participant's poller nonce and the voter nonce.
   *
   * @return Block hasher initialization bytes.
   */
  private byte[][] initHasherByteArrays() {
    int len = getPollSize() + 1; // One for plain hash.
    byte[][] initBytes = new byte[len][];
    initBytes[len-1] = new byte[0];
    int participantIndex = 0;
    synchronized(theParticipants) {
      for (ParticipantUserData ud : theParticipants.values()) {
        log.debug2("Initting hasher byte arrays for voter " + ud.getVoterId());
        initBytes[participantIndex] = ByteArray.concat(ud.getPollerNonce(),
                                         ud.getVoterNonce());
        participantIndex++;
      }
    }
    return initBytes;
  }

  /**
   * Create an array of message digests, one for each participant in the poll.
   * This array is guaranteed to be in the same order as the inner circle
   * iterator and the array of byte arrays returned by
   * {@link #initHasherByteArrays()}
   *
   * @return An array of MessageDigest objects to be used by the BlockHasher.
   */
  private MessageDigest[] initHasherDigests() {
    int len = getPollSize() + 1; // One for plain hash.
    try {
      return PollUtil.createMessageDigestArray(len,
					       pollerState.getHashAlgorithm());
    } catch (NoSuchAlgorithmException ex) {
      // This will have been caught at construction time, and so should
      // never happen here.
      log.critical("Unexpected NoSuchAlgorithmException in " +
		   "initHasherDigests");
      stopPoll(POLLER_STATUS_ERROR);
      return new MessageDigest[0];
    }
  }

  /**
   * Request a repair for the specified URL.  This method appends the URL
   * and its list of disagreeing voters to a queue, which is examined at the
   * end of tallying.
   * 
   * @param url
   * @param disagreeingVoters Set of disagreeing voters.
   */
  private void requestRepair(
     final String url,
     final Collection<ParticipantUserData> disagreeingVoters) {

    if (okToQueueRepair()) {
      PollerStateBean.RepairQueue repairQueue = pollerState.getRepairQueue();
      // Choose where to request the repair.
      log.debug2("Deciding whether to repair from cache or publisher.  Repair "
		 + "from cache probability=" + repairFromCache);
      if (ProbabilisticChoice.choose(repairFromCache) ||
	  (enableRepairFromCache && AuUtil.isPubDown(getAu()))) {
	PeerIdentity peer = findPeerForRepair(disagreeingVoters);
	log.debug2("Requesting repair from " + peer + ": " + url);
	repairQueue.repairFromPeer(url, peer);
      } else {
	log.debug2("Requesting repair from publisher: " + url);
	repairQueue.repairFromPublisher(url);
      }
    }
  }

  private void requestRepairFromPublisher(final String url) {
    if (okToQueueRepair()) {
      PollerStateBean.RepairQueue repairQueue = pollerState.getRepairQueue();
      log.debug2("Requesting repair from publisher: " + url);
      repairQueue.repairFromPublisher(url);
    }
  }

  // Return true iff we haven't queued more than the max allowed number of
  // repairs
  boolean okToQueueRepair() {
    if (maxRepairs < 0) {
      return true;
    }
    PollerStateBean.RepairQueue repairQueue = pollerState.getRepairQueue();
    return repairQueue.size() < maxRepairs;
  }

  /* Select a peer to attempt repair from. */
  PeerIdentity
      findPeerForRepair(Collection<ParticipantUserData> disagreeingVoters) {
    ParticipantUserData voter =
      (ParticipantUserData)CollectionUtil.randomSelection(disagreeingVoters);
    return voter.getVoterId();
  }

  /**
   * Request a repair from the specified peer for the specified URL.  Called
   * from doRepairs().
   */
  private void requestRepairFromPeer(String url, PeerIdentity peer) {
    log.debug2("Requesting repair from " + peer + ": " + url);
    ParticipantUserData ud = getParticipant(peer);
    V3LcapMessage msg = ud.makeMessage(V3LcapMessage.MSG_REPAIR_REQ);
    msg.setTargetUrl(url);
    msg.setEffortProof(null);
    msg.setExpiration(getRepairMsgExpiration());
    try {
      sendMessageTo(msg, peer);
    } catch (IOException ex) {
      log.error("IOException trying to send repair request", ex);
      // XXX: Alerts, retry
    }
  }

  /**
   * Delete (deactivate) the block in our repository.
   * @param url The block to be deleted.
   */
  private void deleteBlock(String url) {
    try {
      // Delete or don't according to plugin wishes.  If plugin doesn't
      // specify follow global setting for AUs for which it's appropriate
      if (AuUtil.isDeleteExtraFiles(getAu(),
				    (deleteExtraFiles &&
				     AuUtil.okDeleteExtraFiles(getAu())))) {
        CachedUrlSetSpec cuss =
          new SingleNodeCachedUrlSetSpec(url);
        CachedUrlSet cus = getAu().makeCachedUrlSet(cuss);
        NodeManager nm = theDaemon.getNodeManager(getAu());
        log.debug("Marking block deleted: " + url);
        nm.deleteNode(cus);
      } else {
        log.info("Asked to mark file " + url + " deleted in poll " +
                 pollerState.getPollKey() + ".  Not actually deleting.");
      }
      pollerState.getRepairQueue().markComplete(url);
    } catch (IOException ex) {
      log.warning("Unable to delete node " + url + " in poll " + getKey(), ex);
    }
  }
  
  /**
   * Called at the end of tallying if there are any pending repairs.
   */
  private void doRepairs() {
    if (!hasQuorum()) {
      log.error("doRepairs() called in inquorate poll");
      return;
    }
    PollerStateBean.RepairQueue queue = pollerState.getRepairQueue();

    List pendingPeerRepairs = queue.getPendingPeerRepairs();
    List pendingPublisherRepairs = queue.getPendingPublisherRepairUrls();
    
    boolean needPublisherRepairs = pendingPublisherRepairs.size() > 0;
    boolean needPeerRepairs = pendingPeerRepairs.size() > 0;
    boolean haveActiveRepairs = queue.getActiveRepairs().size() > 0;
    
    if (needPublisherRepairs || needPeerRepairs) {
      setStatus(V3Poller.POLLER_STATUS_WAITING_REPAIRS);
      if (log.isDebug()) {
        log.debug("Pending Peer Repairs: " + pendingPeerRepairs.size());
        log.debug("Pending Publisher Repairs: " + pendingPublisherRepairs.size());
        log.debug("Active Repairs: " + queue.getActiveRepairs().size());
      }
    } else {
      // It's OK to shortcut and end the poll here, there's nothing left to do!

      // XXX hack to allow voter state machine to send receipt.
      // should wait until all voter state machines have finished
      try {
	Deadline.in(1 * Constants.SECOND).sleep();
      } catch (InterruptedException e) {
	// ignore
      }

      voteComplete();
      return;
    }

    // If we have decided to repair any URLs from the publisher, pass the
    // set of URLs we want to repair to the RepairCrawler.  A callback
    // handles checking each successfully fetched repair.
    if (needPublisherRepairs) {
      log.debug("Starting publisher repair crawl for " + 
                pendingPublisherRepairs.size() + " urls.");
      CrawlManager cm = theDaemon.getCrawlManager();
      CrawlManager.Callback cb = new CrawlManager.Callback() {
        public void signalCrawlAttemptCompleted(boolean success,
                                                Object cookie,
                                                CrawlerStatus status) {
	  log.debug3("Repair crawl complete: " + success + ", fetched: "
		     + status.getUrlsFetched());
          if (success) {
            // Check the repairs.
            // XXX: It would be nice to be able to re-hash the repaired
            // URLs as a single set, but we don't have a notion of
            // a disjoint CachedUrlSetSpec that represents a collection of
            // unrelated nodes.
            Collection urlsFetched = status.getUrlsFetched();
            for (Iterator iter = urlsFetched.iterator(); iter.hasNext(); ) {
              receivedRepair((String)iter.next());
            }
          }
        }
      };

      queue.markActive(pendingPublisherRepairs);
      cm.startRepair(getAu(), pendingPublisherRepairs,
                     cb, null, null);
    }
    
    // If we have decided to repair from any caches, iterate over the list
    // of PollerStateBean.Repair objects and request each one.
    if (needPeerRepairs) {
      log.debug("Requesting repairs from peers for " +
                pendingPeerRepairs.size() + " urls.");

      for (Iterator iter = pendingPeerRepairs.iterator(); iter.hasNext(); ) {
        PollerStateBean.Repair r = (PollerStateBean.Repair)iter.next();
        requestRepairFromPeer(r.getUrl(), r.getRepairFrom());
        queue.markActive(r.getUrl());
      }

    }
  }

  /**
   * Callback used to schedule a small re-check hash when a repair has 
   * been received.
   */
  public void receivedRepair(final String url) {
    // It is possible that a repair may come in after the poll has been closed
    // and its resources released.
    if (isPollCompleted()) {
      log.debug("Repair was received after the poll was closed. " +
                "Poll key = " + getKey());
      // CR: Race?  Have stored content, but not going to check it
      return;
    }
    
    final BlockHasher.EventHandler blockDone =
      new BlockHasher.EventHandler() {
      public void blockDone(final HashBlock hashBlock) {
	// todo(bhayes): This replays the iterators for each
	// hashBlock, rather than waiting for them all, and then
	// re-running them in order. Which might be what the comment
	// below means.

        // CR: should keep track of existence of pending repairs to
        // update status when done
        log.debug3("Finished hashing repair sent for block " + hashBlock);
        // Replay the block comparison using the new hash results.
	UrlTallier urlTallier = makeUrlTallier();
	try {
	  urlTallier.seek(url);
	  BlockTally tally = urlTallier.tallyRepairUrl(url, hashBlock);
	  // NOTE: ParticipantUserData was updated from the initial
	  // pre-repair tally. Results of a repair do not change the
	  // ParticipantUserData.
	  setStatus(V3Poller.POLLER_STATUS_TALLYING);
	  signalNodeAgreement(tally, url);
	  BlockTally.Result result = checkTally(tally, url, false);
	  log.debug3("After-vote hash tally for repaired block " + url
		     + ": " + result.printString);
	  pollerState.getRepairQueue().markComplete(url);
 	} finally {
 	  urlTallier.release();
 	}
      }
    };

    final HashService.Callback hashDone =
      new HashService.Callback() {
        public void hashingFinished(CachedUrlSet urlset,
				    long timeUsed,
				    Object cookie,
				    CachedUrlSetHasher hasher,
				    Exception e) {
              // If there are no more repairs outstanding, go ahead and
          // stop the poll at this point.
          PollerStateBean.RepairQueue queue = pollerState.getRepairQueue();
          // CR: push size() methods lower
          int pending = queue.getPendingRepairs().size();
          int active = queue.getActiveRepairs().size();
          log.debug2("Repair hash on one block done.  Pending repairs="
                     + pending + "; Active repairs=" + active);
          if (pending == 0 && active == 0) {
            voteComplete();
          }
        }
    };

    
    CachedUrlSet blockCus =
      getAu().makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url));
    boolean hashing = scheduleHash(blockCus,
				   repairHashAllVersions ? -1 : 1,
				   Deadline.at(pollerState.getPollDeadline()),
                                   hashDone,
                                   blockDone);
    if (!hashing) {
      // CR: leave repair pending
      log.warning("Failed to schedule a repair check hash for block " + url);
    }
    
  }

  // The vote is over.
  private void voteComplete() {
    if (isPollCompleted()) {
      log.warning("voteComplete() called on a possibly closed poll: "
                  + getKey());
      return;
    }
    computeNewRepairers();
    if (hasQuorum()) {
      // If the poll is quorate, update 

      // Tell the PollManager to hang on to our statistics for this poll.
      PollManager.V3PollStatusAccessor stats = pollManager.getV3Status();
      String auId = getAu().getAuId();
      stats.setAgreement(auId, getPercentAgreement());
      stats.setLastPollTime(auId, TimeBase.nowMs());
      stats.incrementNumPolls(auId);
      // Update the last poll time on the AU.
      AuState auState = theDaemon.getNodeManager(getAu()).getAuState();
      auState.setV3Agreement(getPercentAgreement());
      signalAuEvent();
      stopPoll(POLLER_STATUS_COMPLETE);
    } else {
      stopPoll(V3Poller.POLLER_STATUS_NO_QUORUM);
    }
  }

  // Inform AuEvent listeners of change in AU content
  void signalAuEvent() {
    List<PollerStateBean.Repair> repairs = getCompletedRepairs();
    int nrepairs = repairs.size();
    if (nrepairs > 0) {
      List<String> urls = new ArrayList<String>();
      for (PollerStateBean.Repair rp : repairs) {
	urls.add(rp.getUrl());
      }
      final ArchivalUnit au = getAu();
      final AuEventHandler.ChangeInfo chInfo = new AuEventHandler.ChangeInfo();
      chInfo.setType(AuEventHandler.ChangeInfo.Type.Repair);
      chInfo.setNumUrls(nrepairs);
      chInfo.setUrls(urls);
      chInfo.setAu(au);
      chInfo.setComplete(true);
      PluginManager plugMgr = theDaemon.getPluginManager();
      plugMgr.applyAuEvent(new PluginManager.AuEventClosure() {
			     public void execute(AuEventHandler hand) {
			       hand.auContentChanged(new AuEvent(AuEvent.Type.
			                                         ContentChanged,
			                                         false),
						     au, chInfo);
			     }
	});
    }
  }

  // Independent of quorum, remember peers that agreed with us as they're
  // now entitled to receive repairs
  private void computeNewRepairers() {
    int newRepairees = 0;
    // CR: repair thread holds this lock for a long time?
    synchronized(theParticipants) {
      for (ParticipantUserData ud : theParticipants.values()) {
        PsmInterp interp = ud.getPsmInterp();
	String msg = "Error processing VoteComplete";
	// CR: too soon.  remember error and stop poll at end of loop
	interp.enqueueEvent(V3Events.evtVoteComplete, ehAbortPoll(msg));
        if (log.isDebug2()) {
          log.debug2("Gave peer " + ud.getVoterId()
                     + " the Vote Complete event.");
        }
        
        // Update the participant's agreement history.
	if (!isWillingRepairer(ud.getVoterId()) &&
	    ud.getPercentAgreement() >= pollManager.getMinPercentForRepair()) {
	  newRepairees++;
	}
        this.idManager.signalPartialAgreement(ud.getVoterId(), getAu(), 
                                              ud.getPercentAgreement());
        
      }
    }
    if (newRepairees > 0) {
      String info =
	StringUtil.numberOfUnits(newRepairees,"new agreeing peer",
				 "new agreeing peers");
      log.info(info + " for " + getAu().getName());
      pollerState.setAdditionalInfo(info);
    }
  }

  /**
   * Return the percent agreement for this poll.  Used by the ArchivalUnitStatus
   * accessor, and the V3PollStatus accessor
   */
  public float getPercentAgreement() {
    float agreeingUrls = (float)getAgreedUrls().size();
    float talliedUrls = (float)getTalliedUrls().size();
    float agreement;
    if (agreeingUrls > 0)
      agreement = agreeingUrls / talliedUrls;
    else
      agreement = 0.0f;
    return agreement;
  }

  /**
   * Called by participant state machines if an error occurs.
   *
   * @param id
   * @param errorMsg
   */
  void handleError(PeerIdentity id, String errorMsg) {
    log.error("Peer " + id + " experienced an error. Error =" +
              (errorMsg == null ? "Unknown error" : errorMsg));

    // Drop the voter from the poll.
    removeParticipant(id);
  }

  /**
   * Drop a voter from the poll.
   *
   * @param id
   */

  void removeParticipant(PeerIdentity id) {
    removeParticipant(id, -1);
  }

  void removeParticipant(PeerIdentity id, PollNak nak) {
    removeParticipant(id, -1, nak);
  }

  void removeParticipant(PeerIdentity id, int peerStatus) {
    removeParticipant(id, peerStatus, null);
  }

  void removeParticipant(PeerIdentity id, int peerStatus, PollNak nak) {
    log.debug2("Removing voter " + id + " from poll " +
	       pollerState.getPollKey());
    try {
      synchronized(theParticipants) {
        ParticipantUserData ud = getParticipant(id);
	if (ud == null) {
	  // already removed
	  return;
	}
	if (peerStatus > 0) {
	  ud.setStatus(peerStatus);
	}
        // Release used resources.
	ud.release();
        serializer.removePollerUserData(id);
        theParticipants.remove(id);
	synchronized (exParticipants) {
	  exParticipants.put(id, ud);
	}
	if (nak == PollNak.NAK_NO_AU) {
	  pollerState.addNoAuPeer(id);
	}
        checkpointPoll();
      }
      if (getPollSize() == 0) {
	log.debug("Accelerating next invitation cycle because no participants in poll");

	nextInvitationTime.expire();
      }
    } catch (Exception ex) {
      log.error("Unable to remove voter from poll!", ex);
      stopPoll(V3Poller.POLLER_STATUS_ERROR);
      return;
    }
  }

  /**
   * Checkpoint the per-poll state.
   */
  private void checkpointPoll() {
    if (serializer != null) {
      try {
        serializer.savePollerState(pollerState);
        bytesHashedSinceLastCheckpoint = 0;
      } catch (PollSerializerException ex) {
        log.warning("Unable to save poller state", ex);
      }
    }
  }

  /**
   * Checkpoint the per-participant state.
   * @throws PollSerializerException
   */
  private void checkpointParticipant(ParticipantUserData ud)
      throws PollSerializerException {
    // Should never happen.
    if (ud == null) {
      throw new NullPointerException("checkpointParticipant was passed a " +
      		"null ParticipantUserData reference.");
    }
    // May happen if a deferred action happens after this poll has been
    // cleaned up.
    if (serializer != null) {
      serializer.savePollerUserData(ud);
    }
  }

  /**
   * Used by the participant state machines to send messages to the appropriate
   * voter.
   *
   * @param msg
   * @param to
   * @throws IOException
   */
  void sendMessageTo(V3LcapMessage msg, PeerIdentity to)
      throws IOException {
    if (log.isDebug2()) {
      log.debug2("sendTo(" + msg + ", " + to + ")");
    }
    pollManager.sendMessageTo(msg, to);
  }

  /**
   * Handle an incoming V3LcapMessage.
   *
   */
  public void receiveMessage(LcapMessage message) {
    V3LcapMessage msg = (V3LcapMessage)message;
    PeerIdentity sender = msg.getOriginatorId();
    ParticipantUserData ud = getParticipant(sender);
    if (log.isDebug2()) {
      log.debug2("Received from " + sender + ": " + msg);
    }
    if (ud != null) {
      PsmInterp interp = ud.getPsmInterp();
      PsmMsgEvent evt = V3Events.fromMessage(msg);
      String errmsg = "State machine exception handling message of type "
	+ msg.getOpcodeString() + " from peer "
	+ msg.getOriginatorId() + " in poll "
	+ getKey();
      interp.enqueueEvent(evt, ehIgnore(errmsg));
    } else {
      log.error("No voter user data for peer.  May have " +
                "been removed from poll: " + msg.getOriginatorId());
    }
  }

  public PollerStateBean getPollerStateBean() {
    return pollerState;
  }

  int getTargetSize() {
    return (int)Math.ceil(quorum * targetSizeQuorumMultiplier);
  }

  int getInvitationSize(int targetSize) {
    return (int)Math.ceil(targetSize * invitationSizeTargetMultiplier);
  }

  /**
   * Build the initial set of inner circle peers.
   *
   * @param quorum The number of peers to invite.
   */
  protected void constructInnerCircle(int quorum) {
    Map availMap = getAvailablePeers();
    log.debug3("constructInnerCircle: quorum = " + quorum + ", " +
	       availMap.size() + " available");
    Collection innerCircleVoters = findNPeersToInvite(getTargetSize());
    log.debug2("Selected " + innerCircleVoters.size()
	       + " participants for poll ID " + pollerState.getPollKey());
    addPeersToInnerCircle(innerCircleVoters, false, false);
  }
  
  Collection<PeerIdentity> findNPeersToInvite(int additional) {
    log.debug3("findNPeers: " + additional);
    int invitationTarget = getInvitationSize(additional);
    Map availMap = getAvailablePeers();
    log.debug3("Choosing " + invitationTarget + " of " + availMap.size()
	       + " peers");
    int innerInvite = Math.min(invitationTarget, availMap.size());
    Collection<PeerIdentity> peers =
      CollectionUtil.weightedRandomSelection(availMap, innerInvite);
    return peers;
  }


  // Count the peers who have agreed to participate.
  int countParticipatingPeers() {
    int res = 0;
    for (ParticipantUserData peer : theParticipants.values()) {
      if (peer.isParticipating()) {
	res++;
      }
    }
    return res;
  }

  private Map availablePeers = null;	// maps peerid to invitation weight

  /** Build availablePeers map, or trim it if it already exists.  Map will
   * contain all peers that are (still) eligible to be invited into this
   * poll.  */
  synchronized Map getAvailablePeers() {
    if (availablePeers == null) {
      Collection<PeerIdentity> allPeers;
      // load list of peers who have recently said they don't have the AU
      DatedPeerIdSet noAuSet = pollManager.getNoAuPeerSet(getAu());
      synchronized (noAuSet) {
	try {
	  try {
	    noAuSet.load();
	    int s = noAuSet.size();
	    pollManager.ageNoAuSet(getAu(), noAuSet);
	    log.debug2("NoAuSet: " + s + " aged-> " + noAuSet.size()
		       + ", " + StringUtil.timeIntervalToString(TimeBase.msSince(noAuSet.getDate())));
	  } catch (IOException e) {
	    log.error("Failed to load no AU set", e);
	    noAuSet.release();
	    noAuSet = null;
	  }
	  // first build list of eligible peers
	  if (enableDiscovery) {
	    allPeers =
	      idManager.getTcpPeerIdentities(new EligiblePredicate(noAuSet));
	  } else {
	    Collection<String> keys =
	      CurrentConfig.getList(IdentityManagerImpl.PARAM_INITIAL_PEERS,
				    IdentityManagerImpl.DEFAULT_INITIAL_PEERS);
	    allPeers = new ArrayList();
	    for (String key : keys) {
	      try {
		PeerIdentity id = idManager.findPeerIdentity(key);
		if (isPeerEligible(id, noAuSet)) {
		  allPeers.add(id);
		}
	      } catch (IdentityManager.MalformedIdentityKeyException e) {
		log.warning("Can't add to inner circle: " + key, e);
	      }
	    }
	  }
	} finally {
	  if (noAuSet != null) {
	    noAuSet.release();
	  }
	}
      }
      // then build map including invitation weight for each peer.
      availablePeers = new HashMap();
      for (PeerIdentity id : allPeers) {
	availablePeers.put(id, invitationWeight(id));
      }
      log.debug2("Found available peers: " + availablePeers.size());
    } else {
      // if map already exists, remove peers no longer eligible (becuase
      // they've already been invited)
      for (Iterator iter = availablePeers.entrySet().iterator();
	   iter.hasNext(); ) {
	Map.Entry ent = (Map.Entry)iter.next();
	PeerIdentity pid = (PeerIdentity)ent.getKey();
	if (!isPeerEligible(pid)) {
	  iter.remove();
	}
      }
      log.debug2("Pruned available peers: " + availablePeers.size());
    }
    return availablePeers;
  }

  class EligiblePredicate implements Predicate {
    private DatedPeerIdSet noAuSet;

    EligiblePredicate(DatedPeerIdSet noAuSet) {
      this.noAuSet = noAuSet;
    }

    public boolean evaluate(Object obj) {
	if (obj instanceof PeerIdentity) {
	  return isPeerEligible((PeerIdentity)obj, noAuSet);
	}
	return false;
      }};

  private boolean isPeerEligible(String pidKey)
      throws IdentityManager.MalformedIdentityKeyException {
    return isPeerEligible(idManager.findPeerIdentity(pidKey));
  }
  
  boolean isPeerEligible(PeerIdentity pid) {
    return isPeerEligible(pid, null);
  }

  boolean isPeerEligible(PeerIdentity pid, DatedPeerIdSet noAuSet) {
    // never include a local id.
    if (pid.isLocalIdentity()) {
      return false;
    }
    // never include a peer that's already participating or has already
    // dropped out of this poll
    synchronized (theParticipants) {
      if (theParticipants.containsKey(pid)) {
	return false;
      }
      synchronized (exParticipants) {
	if (exParticipants.containsKey(pid)) {
	  return false;
	}
      }
    }
    // don't include peers on our subnet if told not to
    if (pollManager.isNoInvitationSubnet(pid)) {
      return false;
    }

    try {
      if (noAuSet != null && noAuSet.contains(pid)) {
	if (log.isDebug2()) {
	  log.debug2("Not eligible, no AU: " + pid);
	}
	return false;
      }
    } catch (IOException e) {
      // impossible with loaded PersistentPeerIdSet
    }

    PeerIdentityStatus status = idManager.getPeerIdentityStatus(pid);
    if (status == null) {
      log.warning("No status for peer: " + pid);
      return true;
    }

    // Never include a peer whose groups are known to be disjoint with ours
    if (!isGroupMatch(status)) {
      return false;
    }
    return true;
  }

  boolean isGroupMatch(PeerIdentityStatus status) {
    List groups = status.getGroups();
    // if we haven't recorded a group, allow it
    if (groups == null || groups.isEmpty()) {
      return true;
    }
    if (CollectionUtils.containsAny(groups,
				    ConfigManager.getPlatformGroupList())) {
      return true;
    }
    if (TimeBase.msSince(status.getLastGroupTime()) >
	pollManager.getWrongGroupRetryTime()) {
      // Don't want to keep trying him
      status.setLastGroupTime(TimeBase.nowMs());
      return true;
    }
    return false;      
  }

  /**
   * Compute the weight that a peer should be considered for
   * invitation into the poll.
   *  
   * @param status
   * @return A double between 0.0 and 1.0 representing the weight
   *      that we want to try to invite this peer into a poll.
   */
  double invitationWeight(PeerIdentity pid) {
    return invitationWeight(idManager.getPeerIdentityStatus(pid));
  }

  double invitationWeight(PeerIdentityStatus status) {
    return weightResponsiveness(status)
      * weightAtRisk(status)
      * weightPotentialGain(status);
  }

  double weightResponsiveness(PeerIdentityStatus status) {
    CompoundLinearSlope invitationWeightCurve =
      pollManager.getInvitationWeightAgeCurve();
    if (invitationWeightCurve  == null) {
      return 1.0;
    }
    long lastPollInvitationTime = status.getLastPollInvitationTime();
    long lastMessageTime = status.getLastMessageTime();
    long noResponseFor = lastPollInvitationTime - lastMessageTime;
    return invitationWeightCurve.getY(noResponseFor);
  }

  double weightAtRisk(PeerIdentityStatus status) {
    Set<PeerIdentity> peers = pollManager.getPeersWithAuAtRisk(getAu());
    if (peers == null || !peers.contains(status.getPeerIdentity())) {
      return 1.0;
    }
    if (log.isDebug2()) {
      log.debug2("At risk AU instance: " + status.getPeerIdentity() +
		 ", " + getAu().getName());
    }
    return pollManager.getInvitationWeightAtRisk();
  }

  boolean isWillingRepairer(PeerIdentity id) {
    double highest = idManager.getHighestPercentAgreement(id, getAu());
    return highest >= pollManager.getMinPercentForRepair();
  }

  double weightPotentialGain(PeerIdentityStatus status) {
    if (isWillingRepairer(status.getPeerIdentity())) {
      return pollManager.getInvitationWeightAlreadyRepairable();
    }
    return 1.0;
  }

  void addPeersToInnerCircle(Collection<PeerIdentity> peers,
			     boolean start, boolean resuming) {
    for (PeerIdentity id : peers) {
      ParticipantUserData ud = addInnerCircleVoter(id);
      if (start) {
	startParticipant(ud, resuming);
      }
    }
  }

  boolean enlargeInnerCircle() {
    synchronized (theParticipants) {
      int excess = countParticipatingPeers() - getTargetSize();
      if (excess >= 0) {
	log.debug("[InvitationCallback] Enough peers are participating (" +
		  getQuorum() + "+" + excess + ")");
	return false;
      }
      
      Collection<PeerIdentity> newPeers = findNPeersToInvite(-excess);
      if (newPeers.isEmpty()) {
	log.debug("[InvitationCallback] No more peers to invite");
	return false;
      } else {
	log.debug("[InvitationCallback] Inviting " + newPeers.size()
		  + " new peers to participate.");
	addPeersToInnerCircle(newPeers, true, false);
	return true;
      }
    }
  }

  // The participants should not change after this call.
  void lockParticipants() {
    // todo(bhayes): Nobody enforces, nobody checks.
    urlTallier = makeUrlTallier();
  }

  UrlTallier makeUrlTallier() {
    synchronized(theParticipants) {
	return new UrlTallier(new ArrayList(theParticipants.values()));
    }
  }

  Class getPollerActionsClass() {
    return PollerActions.class;
  }
  
  /**
   * Check to see whether enough pollers have agreed to participate
   * with us.  If not, invite more, and schedule another check.
   */
  private class InvitationCallback implements TimerQueue.Callback {
    public void timerExpired(Object cookie) {
      // Check to see if the poll has ended.  If so, immediately return.
      if (!activePoll) return;

      if (enlargeInnerCircle() && isPollActive()) {
	// Schedule another check
	nextInvitationTime.expireIn(timeBetweenInvitations);
	invitationRequest =
	  TimerQueue.schedule(nextInvitationTime,
			      new InvitationCallback(),
			      cookie);
      } else {
	if (getPollSize() == 0) {
	  log.debug("Tallying early because no participants and no more to invite");
	  voteTallyStart.expire();
	}
      }
    }

    public String toString() {
      return "Poll Invitation Callback [" + getKey() + "]";
    }
  }

  /**
   * This callback is called when the vote tally deadline has expired.
   * If enough peers have cast votes, the tally can go forward, and the
   * callback schedules a hash.
   *
   */
  private class VoteTallyCallback implements TimerQueue.Callback {
    
    private boolean tallyStarted = false;

    public void timerExpired(Object cookie) {
      
      if (!isPollActive()) return;

      synchronized (this) {
	if (tallyStarted) {
	  return;
	}
	tallyStarted = true;
      }
      
      // Ensure that the invitation timer is cancelled.
      if (invitationRequest != null) {
        TimerQueue.cancel(invitationRequest);
        invitationRequest = null;
      }
      log.debug2("Vote Tally deadline reached: " + pollerState.getPollKey());

      Collection<PeerIdentity> noAuPeers = pollerState.getNoAuPeers();
      if (noAuPeers != null && !noAuPeers.isEmpty()) {
	HistoryRepository historyRepo = theDaemon.getHistoryRepository(getAu());
	DatedPeerIdSet noAuSet = pollManager.getNoAuPeerSet(getAu());
	synchronized (noAuSet) {
	  try {
	    if (log.isDebug2()) {
	      log.debug2("Poll " + getKey() + " Adding no AU peers: "
			 + noAuPeers);
	    }
	    noAuSet.load();
	    // Reset date if set is empty.  Slightly better to do here than
	    // when set is emptied (due to age) as this doesn't start the
	    // clock until some entries are actually added to the set.
	    if (noAuSet.isEmpty()) {
	      noAuSet.setDate(TimeBase.nowMs());
	    }
	    int s = noAuSet.size();
	    noAuSet.addAll(noAuPeers);
	    if (log.isDebug2()) {
	      log.debug2("NoAuSet: " + s + " -> " + noAuSet.size()
			 + ", " + StringUtil.timeIntervalToString(TimeBase.msSince(noAuSet.getDate())));
	    }
	    noAuSet.store(true);
	  } catch (IOException e) {
	    log.error("Failed to update no AU set", e);
	  } finally {
	    noAuSet.release();
	  }
	}	
      }

      // Prune "theParticipants", and remove any who have not cast a vote.
      // Iterate over a COPY of the participants, to avoid concurrent
      // modifications.
      Collection<PeerIdentity> peerListCopy;
      synchronized (theParticipants) {
	peerListCopy = new ArrayList(theParticipants.keySet());
      }
      for (PeerIdentity id : peerListCopy) {
	ParticipantUserData ud = getParticipant(id);
	if (ud == null) {
	  // already removed
	  continue;
	}
        if (!pollerState.hasPeerVoted(id)) {
	  // XXX move to state machine
	  switch (ud.getStatus()) {
	  case PEER_STATUS_WAITING_POLL_ACK:
	  case PEER_STATUS_NO_RESPONSE:
	    removeParticipant(id, PEER_STATUS_NO_RESPONSE);
	    break;
	  default:
	    removeParticipant(id, PEER_STATUS_NO_VOTE);
	    break;
	  }
        }
      }

      // Determine whether enough peers have voted to reach a quorum.
      if (!hasQuorum()) {
        log.warning("Fewer participant votes (" + getPollSize() +
		    ") than quorum (" + getQuorum() + ") in poll " + getKey());
	// continue with tally even if no quorum so that we may become a
	// willing repaier for any peers that voted and agree with ut.

	// Remove any voting participants for which we are already a
	// willing repairer
	synchronized(theParticipants) {
	  List<PeerIdentity> toRemove = new ArrayList();
          for (PeerIdentity id : theParticipants.keySet()) {
	    if (isWillingRepairer(id)) {
	      toRemove.add(id);
	    }
	  }
	  log.debug2("Removing existing repairers " + toRemove);
          for (PeerIdentity id : toRemove) {
	    removeParticipant(id, PEER_STATUS_COMPLETE);
	  }
	}
	int nleft = getPollSize();
	if (nleft == 0) {
	  // nobody left, stop poll and don't tally
	  stopPoll(V3Poller.POLLER_STATUS_NO_QUORUM);
	  return;
	}
	log.debug("Contining to tally " + nleft + " potential repairers");
      }
      log.debug2("Scheduling hash: " + pollerState.getPollKey());
      // XXX: Refactor when our hash can be associated with an
      //      existing step task.
      if (task != null) task.cancel();
      CachedUrlSet cus = pollerState.getCachedUrlSet();
      // At this point theParticipants is fixed, and should not be changed.
      lockParticipants();
      if (!scheduleHash(cus,
			Deadline.at(tallyEnd),
			new HashingCompleteCallback(),
			new BlockEventHandler())) {
	long hashEst = cus.estimatedHashDuration();
	String msg = "No time for " + 
	  StringUtil.timeIntervalToString(hashEst) + " hash between " +
	  TimeBase.nowDate() + " and " + Deadline.at(tallyEnd);
	log.error(msg + ": " + pollerState.getPollKey());
	pollerState.setErrorDetail(msg);
	stopPoll(POLLER_STATUS_NO_TIME);
      }
      pollerState.hashStarted(true);
      checkpointPoll();
    }
    public String toString() {
      return "V3 Poll Tally";
    }
  }

  /**
   * Callback called by the poll timer to signal that the poll should end.
   *
   */
  private class PollCompleteCallback implements TimerQueue.Callback {
    /**
     * Called when the poll timer expires.
     *
     * @param cookie data supplied by caller to schedule()
     */
    public void timerExpired(Object cookie) {
      log.debug("Poll time has expired.");
      if (pollerState.expectingRepairs() && extraPollTime > 0) {
        log.debug("Still expecting some repairs.  Holding the poll open for " +
                 "another " + extraPollTime + " ms.");
        
        // Compute the new deadline, and replace the existing deadline.
        Deadline newDeadline =
          Deadline.at(pollerState.getPollDeadline() + extraPollTime);
        TimerQueue.schedule(newDeadline, new ExtraTimeCallback(), null);
        pollerState.setPollDeadline(newDeadline.getExpirationTime());
        return;
      } else {
        log.debug("No expected repairs remain.  Ready to end the poll.");
      }
      voteComplete();
    }

    public String toString() {
      return "V3 Poller " + getKey();
    }
  }
  
  /** Callback used if extra poll time is requested at the initial poll 
   * deadline. */
  private class ExtraTimeCallback implements TimerQueue.Callback {
    public void timerExpired(Object cookie) {
      log.debug("Extra time for the poll has expired.  Ending the poll " +
                "whether we expect repairs or not.");
      if (log.isDebug() && pollerState.expectingRepairs()) {
        log.warning("Ending the poll while repairs are still expected.");
      }
      voteComplete();
    }
    
    public String toString() {
      return "V3 Poller " + getKey();
    }
  }

  /**
   * Callback called after all blocks have been hashed during our tally.
   */
  private class HashingCompleteCallback implements HashService.Callback {
    /**
     * Called when the timer expires or hashing is complete.
     *
     * @param cookie data supplied by caller to schedule()
     */
    public void hashingFinished(CachedUrlSet cus, long timeUsed, Object cookie,
                                CachedUrlSetHasher hasher, Exception e) {
      if (e != null) {
        log.warning("Poll hash failed", e);
	// CR: too extreme.  what if pending repairs?
        stopPoll(POLLER_STATUS_ERROR);
      } else if (hasQuorum()) {
        finishTally();
      } else {
	voteComplete();
      }
    }
  }

  /**
   * Callback called after each block has been hashed during our tally.
   */
  // CR: look into having this send event to state machine, run tally there
  private class BlockEventHandler implements BlockHasher.EventHandler {
    public void blockDone(HashBlock hashBlock) {
      if (pollerState != null) {
        tallyBlock(hashBlock);
      }
    }
  }
  
  public LockssApp getLockssDaemon() {
    return theDaemon;
  }

  /*
   * BasePoll implementations.
   */

  /**
   * Set the message that created this poll.  V3 does not provide an
   * implementation.
   */
  public void setMessage(LcapMessage msg) {
    // Not implemented.
  }

  /**
   * Get the poll creation time.
   */
  public long getCreateTime() {
    return pollerState.getCreateTime();
  }
  
  /* The time the poll actually ended, or -1 if not ended yet. */
  public long getEndTime() {
    return pollerState.getPollEnd();
  }
  
  /**
   * Return the serialization state directory used by this poll.
   */
  public File getStateDir() {
    if (serializer != null) {
      return serializer.pollDir;
    }
    return null;
  }

  public boolean isEnableHashStats() {
    return enableHashStats;
  }

  /**
   * Not used by V3
   */
  public PeerIdentity getCallerID() {
    return pollerState.getPollerId();
  }

  /**
   * Not used by V3
   */
  protected boolean isErrorState() {
    return false;
  }

  synchronized public boolean isPollActive() {
    return activePoll;
  }

  synchronized public boolean isPollCompleted() {
    return !activePoll;
  }

  /** Close the poll.
      @return true if the poll was previously open. */
  synchronized boolean checkAndCompletePoll() {
    boolean previous = this.activePoll;
    this.activePoll = false;
    return previous;
  }

  /**
   * Not used by V3
   */
  public boolean isMyPoll() {
    return true;
  }

  /**
   * Return the PollSpec for this poll.
   */
  public PollSpec getPollSpec() {
    return pollerState.getPollSpec();
  }

  /**
   * Return the CachedUrlSet for this poll.
   */
  public CachedUrlSet getCachedUrlSet() {
    return pollerState.getCachedUrlSet();
  }

  /**
   * Return the poll version.
   */
  public int getVersion() {
    return pollerState.getProtocolVersion();
  }
  
  /**
   * Return the ID Manager.
   */
  public IdentityManager getIdentityManager() {
    return idManager;
  }

  /**
   * Return the message that started this poll.  V3Poller does not provide
   * an implementation of this method.
   */
  public LcapMessage getMessage() {
    throw new UnsupportedOperationException("V3Poller does not suppor " +
                "Poller.getMessage()");
  }

  /**
   * Return the size of the poll.
   */
  public int getPollSize() {
    synchronized(theParticipants) {
      return theParticipants.size();
    }
  }

  private ParticipantUserData getParticipant(PeerIdentity id) {
    synchronized(theParticipants) {
      return (ParticipantUserData)theParticipants.get(id);
    }
  }

  /**
   * Return the poll key.
   */
  public String getKey() {
    return pollerState.getPollKey();
  }

  public Deadline getDeadline() {
    return Deadline.restoreDeadlineAt(pollerState.getPollDeadline());
  }

  public long getPollExpiration() {
    return pollerState.getPollDeadline();
  }

  public long getDuration() {
    return pollerState.getDuration();
  }

  public int getQuorum() {
    return pollerState.getQuorum();
  }

  public int getVoteMargin() {
    return pollerState.getVoteMargin();
  }

  private boolean hasQuorum() {
    return getPollSize() >= getQuorum();
  }

  public List<ParticipantUserData> getParticipants() {
    synchronized(theParticipants) {
      return new ArrayList(theParticipants.values());
    }
  }

  public List<ParticipantUserData> getExParticipants() {
    synchronized(exParticipants) {
      return new ArrayList(exParticipants.values());
    }
  }

  public PollTally getVoteTally() {
    throw new UnsupportedOperationException("Not implemented for V3");
  }

  public int getType() {
    return Poll.V3_POLL;
  }

  public ArchivalUnit getAu() {
    return pollerState.getCachedUrlSet().getArchivalUnit();
  }

  public String getStatusString() {
    return V3Poller.POLLER_STATUS_STRINGS[pollerState.getStatus()];
  }
  
  public int getStatus() {
    return pollerState.getStatus();
  }

  private void setStatus(int status) {
    if (pollerState.getStatus() != status) {
      if (log.isDebug2()) {
	log.debug2("Poll status: " + getStatusString() + " -> " +
		   V3Poller.POLLER_STATUS_STRINGS[status]);
      }
      pollerState.setStatus(status);
      checkpointPoll();
    }
  }

  public static String getStatusString(int status) {
    return V3Poller.POLLER_STATUS_STRINGS[status];
  }
  
  public long getVoteDeadline() {
    return pollerState.getVoteDeadline();
  }
  
  public long getVoteDuration() {
    return pollerState.getVoteDuration();
  }

  public long getPollMsgExpiration() {
    return TimeBase.nowMs() + timeBetweenInvitations;
  }

  public long getRepairMsgExpiration() {
    return pollerState.getPollDeadline() + extraPollTime;
  }

  public List getActiveRepairs() {
    return pollerState.getRepairQueue().getActiveRepairs();
  }

  public List getCompletedRepairs() {
    return pollerState.getRepairQueue().getCompletedRepairs();
  }

  public PollerStateBean.RepairQueue getRepairQueue() {
    return pollerState.getRepairQueue();
  }

  public List getTalliedUrls() {
    List allUrls = new ArrayList();
    allUrls.addAll(getAgreedUrls());
    allUrls.addAll(getDisagreedUrls());
    allUrls.addAll(getTooCloseUrls());
    allUrls.addAll(getNoQuorumUrls());
    return allUrls;
  }

  public Set getAgreedUrls() {
    return pollerState.getTallyStatus().getAgreedUrls();
  }

  public Set getDisagreedUrls() {
    return pollerState.getTallyStatus().getDisagreedUrls();
  }

  public Set getTooCloseUrls() {
    return pollerState.getTallyStatus().getTooCloseUrls();
  }

  public Set getNoQuorumUrls() {
    return pollerState.getTallyStatus().getNoQuorumUrls();
  }
  
  public Map<String,String> getErrorUrls() {
    return pollerState.getTallyStatus().getErrorUrls();
  }

  public long getBytesHashed() {
    return totalBytesHashed;
  }
  
  public long getBytesRead() {
    return totalBytesRead;
  }
  
  /**
   * Release members not used after the poll has been closed.
   */
  public void release() {
    pollerState.release();
    
    if (urlTallier != null) {
      urlTallier.release();
    }

    synchronized(theParticipants) {
      for (ParticipantUserData ud : theParticipants.values()) {
        ud.release();
      }
    }
    stateDir = null;
    pollCompleteRequest = null;
    voteCompleteRequest = null;
    task = null;
    serializer = null;
    pollManager = null;
    idManager = null;
    urlTallier = null;
  }

  private PsmMachine makeMachine() {
    try {
      PsmMachine.Factory fact = PollerStateMachineFactory.class.newInstance();
      return fact.getMachine(getPollerActionsClass());
    } catch (Exception e) {
      String msg = "Can't create poller state machine";
      log.critical(msg, e);
      throw new RuntimeException(msg, e);
    }
  }
}
