package SCBot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import SCBot.RegionGraph.RegionStatus;
import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class AlzaBot1 extends DefaultBWListener {

	private Mirror mirror = new Mirror();

	private Game game;

	private Player self;

	// state
	private boolean isTimeToDrone;
	private boolean antiAirSpotted;
	private int currentStepNumber;

	private List<TilePosition> enemyStartingLocation;
	private List<DrawInfo> shapes = new ArrayList<DrawInfo>();

	private EconomyDirector economy;

	private HashMap<Integer, Unit> knownEnemyUnits;
	private HashMap<Integer, Unit> knownEnemyBuildings;

	private ArrayList<BuildStep> buildOrder;

	private LinkedList<Army> armies;
	private Army stagingArmy;

	private RegionGraph regions;

	private enum ConditionType {
		SUPPLY, BUILDING, TECH
	}

	private Queue<BuildingPlan> buildingQueue;
	private List<BuildingPlan> activeBuildPlans;
	private Queue<UnitType> unitQueue;
	private Queue<ResearchType> researchQueue;

	// for periodic tasks that should not be done simultaneously
	public static final int FRAME_INTERVAL_ONE = 499;
	public static final int FRAME_INTERVAL_TWO = 503;

	// TODO this is becoming unwieldy, needs rework
	private class BuildStep {
		private final ConditionType conditionType;
		private final int conditionCount;
		private final UnitType conditionUnitType;
		private final UnitType effectUnitType;
		private final ResearchType effectUpgradeType;
		private final int effectUnitCount;
		private final boolean continueDroning;
		private int startFrame = -1;
		private UnitType endConditionType;
		private int endConditionCount;

		BuildStep(ConditionType t, int c, UnitType u, int i, boolean b) {
			this.conditionType = t;
			this.conditionCount = c;
			this.effectUnitType = u;
			this.effectUnitCount = i;
			this.continueDroning = b;
			this.conditionUnitType = null;
			this.effectUpgradeType = null;
		}

		BuildStep(ConditionType t, int c, UnitType conditionKind, UnitType effectType, int i, boolean b) {
			this.conditionType = t;
			this.conditionCount = c;
			this.effectUnitType = effectType;
			this.effectUnitCount = i;
			this.continueDroning = b;
			this.conditionUnitType = conditionKind;
			this.effectUpgradeType = null;
		}

		public BuildStep(ConditionType t, int c, UnitType conditionKind, UpgradeType upgrade, boolean b) {
			this.conditionType = t;
			this.conditionCount = c;
			this.effectUnitType = null;
			this.effectUpgradeType = new ResearchType(upgrade);
			this.effectUnitCount = -1;
			this.continueDroning = b;
			this.conditionUnitType = conditionKind;
		}

		public BuildStep(ConditionType t, int c, UnitType conditionKind, TechType upgrade, boolean b) {
			this.conditionType = t;
			this.conditionCount = c;
			this.effectUnitType = null;
			this.effectUpgradeType = new ResearchType(upgrade);
			this.effectUnitCount = -1;
			this.continueDroning = b;
			this.conditionUnitType = conditionKind;
		}

		public int getStartFrame() {
			return this.startFrame;
		}

		public void setStartFrame(int startFrame) {
			if (this.startFrame != -1)
				System.err.println("Trying to set startFrame that is already set.");
			this.startFrame = startFrame;
		}

		public boolean isConditionMet() {
			switch (this.conditionType) {
			case SUPPLY:
				return (AlzaBot1.this.self.supplyUsed() >= this.conditionCount * 2);
			case BUILDING:
				for (Unit u : AlzaBot1.this.self.getUnits()) {
					if (u.getType() == this.conditionUnitType)
						return u.isCompleted();
				}
			}
			return false;
		}

		public boolean isCompleted() {
			return getUnitCount(this.endConditionType, null) >= this.endConditionCount && this.startFrame > -1;
		}

		public boolean followStep() {
			AlzaBot1.this.isTimeToDrone = this.continueDroning;
			if (this.effectUnitType != null) {
				if (this.effectUnitType.isBuilding()) {
					queueBuilding(this.effectUnitType, AlzaBot1.this.self.getStartLocation());
					this.endConditionType = this.effectUnitType;
					this.endConditionCount = getUnitCount(this.endConditionType, null) + 1;
				} else {
					queueUnit(this.effectUnitType, this.effectUnitCount);
					this.endConditionType = this.effectUnitType;
					this.endConditionCount = getUnitCount(this.endConditionType, null) + this.effectUnitCount;
				}
			} else {
				if (this.effectUpgradeType != null) {
					queueResearch(this.effectUpgradeType);
				}
			}

			return false;
		}
	}

	private class DrawInfo {
		public DrawInfo(Position position, Position position2, Color c, boolean b) {
			this.pos1 = position;
			this.pos2 = position2;
			this.color = c;
			this.opaque = b;
		}

		public DrawInfo(Position position, String text) {
			this.pos1 = position;
			this.text = text;
		}

		public Position pos1;
		public Position pos2;
		public String text;
		public Color color;
		public boolean opaque = false;
	}

	private class BuildingPlan {
		public BuildingPlan(UnitType unitType, TilePosition buildTile) {
			this.building = unitType;
			this.buildingTile = buildTile;
		}

		public UnitType building;
		public TilePosition buildingTile;
		public Unit builder;
		public Unit vespeneGeyser; // special case
		public int startFrame;

		public boolean execute() {
			if (AlzaBot1.this.game.getFrameCount() - this.startFrame > 1000) {
				AlzaBot1.this.activeBuildPlans.remove(this); // if something has gone wrong making a building, restart the process
				AlzaBot1.this.buildingQueue.add(this);
				System.out.println("building plan resetting: " + this.building);
			}
			return this.builder.build(this.building, this.buildingTile);
		}
	}

	public void run() {
		this.mirror.getModule().setEventListener(this);

		this.mirror.startGame();
	}

	@Override
	public void onStart() {
		this.game = this.mirror.getGame();
		this.self = this.game.self();

		this.buildOrder = new ArrayList<BuildStep>(20);
		this.buildOrder.add(new BuildStep(ConditionType.SUPPLY, 9, UnitType.Zerg_Spawning_Pool, 1, true));
		this.buildOrder.add(new BuildStep(ConditionType.SUPPLY, 10, UnitType.Zerg_Hatchery, 1, true));
		this.buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Spawning_Pool, UnitType.Zerg_Zergling, 3, true));
		this.buildOrder.add(new BuildStep(ConditionType.SUPPLY, 9, UnitType.Zerg_Extractor, 1, true));
		this.buildOrder.add(new BuildStep(ConditionType.SUPPLY, 14, UnitType.Zerg_Hydralisk_Den, 1, true));
		this.buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Hydralisk_Den, UnitType.Zerg_Hydralisk, 6, true));
		this.buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Hydralisk_Den, UpgradeType.Muscular_Augments, true));

		this.shapes = new ArrayList<DrawInfo>();

		// econ / macro
		this.economy = new EconomyDirector(this.game);
		this.buildingQueue = new ArrayDeque<BuildingPlan>();
		this.researchQueue = new ArrayDeque<ResearchType>();
		this.activeBuildPlans = new ArrayList<BuildingPlan>();
		this.unitQueue = new ArrayDeque<UnitType>();
		this.isTimeToDrone = true;
		this.currentStepNumber = 0;

		// scouting / army
		this.knownEnemyUnits = new HashMap<Integer, Unit>(100);
		this.knownEnemyBuildings = new HashMap<Integer, Unit>(100);
		this.stagingArmy = null;
		this.armies = new LinkedList<Army>();
		this.antiAirSpotted = false;
		CombatManager.init(this.game);

		System.out.println("JESUSWTF");
		this.game.setLocalSpeed(0);

		// Use BWTA to analyze map
		// This may take a few minutes if the map is processed first time!
		System.out.println("Analyzing map...");
		BWTA.readMap();
		BWTA.analyze();
		System.out.println("Map data ready");

		this.enemyStartingLocation = this.game.getStartLocations();
		this.enemyStartingLocation.remove(this.self.getStartLocation());

		this.regions = new RegionGraph(this.game);
		this.economy.setRegions(this.regions);

		int i = 0;
		for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
			System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
			for (Position position : baseLocation.getRegion().getPolygon().getPoints()) {
				System.out.print(position + ", ");
			}
			System.out.println();
		}

		// game.sendText("black sheep wall");
		this.game.enableFlag(1);
	}

	public void onUnitDiscover(Unit u) {
		this.regions.recordUnitDiscovery(u);
		if (!this.antiAirSpotted) {
			UnitType type = u.getType();
			if (type == UnitType.Terran_Barracks || type == UnitType.Protoss_Cybernetics_Core || type == UnitType.Protoss_Photon_Cannon) {
				this.antiAirSpotted = true;
				// TODO zerg antiair
			}
		}
	}

	@Override
	public void onUnitCreate(Unit unit) {
		// rarely used for Zerg
	}

	public void onUnitDestroy(Unit unit) {
		this.regions.recordUnitDestruction(unit);
		if (unit.getPlayer() == this.self) {
			if (unit.getType().isBuilding())
				queueBuilding(unit.getType(), unit.getTilePosition());
			if (unit.getBuildType() == UnitType.Zerg_Overlord)
				this.economy.morphingOverlordsChange(-1);
		}
	}

	public void onUnitComplete(Unit unit) {
		if (unit.getPlayer() != this.self)
			return;
		if (unit.getType() == UnitType.Zerg_Extractor) {
			int workersOnGas = 0;
			for (Unit u : this.game.getUnitsInRadius(unit.getPosition(), 300)) {
				if (u.getType().isWorker() && (u.isIdle() || u.isGatheringMinerals() && !u.isCarryingMinerals())) {
					u.gather(unit);
					if (++workersOnGas >= 3)
						break;
				}
			}
		}
		if (unit.getType() == UnitType.Zerg_Overlord)
			this.economy.morphingOverlordsChange(-1);

		if (unit.getType() == UnitType.Zerg_Hatchery) {
			if (this.stagingArmy != null) {
				this.stagingArmy.moveTo(this.regions.getNewestRegionWithStatus(RegionStatus.ALLIED).getCenter());
			}
		}

		if (unit.getType() == UnitType.Zerg_Zergling || unit.getType() == UnitType.Zerg_Hydralisk) {
			if (this.stagingArmy == null) {
				this.stagingArmy = new Army(unit, this.regions.getNewestRegionWithStatus(RegionStatus.ALLIED).getCenter(), this.game);
			} else {
				if (this.stagingArmy.addUnit(unit) > 10) {
					this.stagingArmy.navigateTo(this.stagingArmy.getArmyAverage(), this.regions.getNewestRegionWithStatus(RegionStatus.ENEMY).getCenter());
					this.armies.add(this.stagingArmy);
					this.stagingArmy = null;
				}
			}
		}
	}

	@Override
	public void onUnitMorph(Unit unit) {
		if (unit.getPlayer() != this.self) {
			return;
		}
		if (unit.getType().isBuilding()) {
			this.regions.recordUnitDiscovery(unit);
			for (BuildingPlan plan : this.activeBuildPlans) {
				if (plan.builder.getID() == unit.getID() || (plan.vespeneGeyser != null && plan.vespeneGeyser.getID() == unit.getID())) {
					this.activeBuildPlans.remove(plan);
					this.economy.unreserveResources(unit.getType());
					break;
				}

			}

		}
		if (unit.getBuildType() == this.unitQueue.peek()) {
			this.unitQueue.poll();
		}
		if (unit.getBuildType() == UnitType.Zerg_Overlord) {
			this.economy.morphingOverlordsChange(1);
		}
	}

	@Override
	public void onFrame() {
		int currentFrame = this.game.getFrameCount();
		this.economy.updateIncome();
		this.game.drawTextScreen(10, 10, "Playing as " + this.self.getName() + " - " + this.self.getRace());

		BuildStep currentStep = this.buildOrder.get(this.currentStepNumber);
		if (currentStep.isConditionMet()) {
			if (currentStep.getStartFrame() == -1) {
				currentStep.setStartFrame(currentFrame);
				currentStep.followStep();
				if (this.buildOrder.size() > this.currentStepNumber + 1)
					this.currentStepNumber++;
			}
		}

		this.regions.updateRegionStatuses();
		for (Army army : this.armies) {
			if (army.removeDeadMembers() == 0) {
				this.armies.remove(army);
			}
			this.game.drawCircleMap(army.followPath(), 3, Color.Cyan, true);
			this.game.drawCircleMap(army.getArmyAverage(), 3, Color.Green, true);
		}

		/*
		 * if (stagingArmy != null) { game.drawCircleMap(stagingArmy.followPath(), 3, Color.Cyan, true); stagingArmy.removeDeadMembers(); Position p = stagingArmy.getArmyCentre(); game.drawCircleMap(p, 3, Color.Red, true); p =
		 * stagingArmy.getArmyAverage(); game.drawCircleMap(p, 3, Color.Green, true); }
		 */
		this.game.drawTextScreen(10, 25, "Reserved Minerals: " + this.economy.getReservedMinerals() + "\nReserved Gas: " + this.economy.getReservedGas() + "\nCurrent Mineral Income: " + this.economy.getMineralIncome()
				+ "\nCurrent Gas Income: " + this.economy.getGasIncome() + "\nCurrent Build Step: " + this.currentStepNumber + "\nNext Unit: " + this.unitQueue.peek());

		int hatcheryBuildPlanCount = 0;
		for (BuildingPlan plan : this.activeBuildPlans) {
			plan.execute();
			if(plan.building == UnitType.Zerg_Hatchery) {
				hatcheryBuildPlanCount++;
			}
		}

		// macro!
		if (this.economy.shouldExpand(hatcheryBuildPlanCount)) {
			queueBuilding(UnitType.Zerg_Hatchery, this.self.getStartLocation());
		}

		// TODO move elsewhere
		if (this.enemyStartingLocation.size() > 1) {
			for (TilePosition possibleBase : this.enemyStartingLocation) {
				if (this.game.isVisible(possibleBase)) {
					if (this.game.getUnitsOnTile(possibleBase).isEmpty()) {
						this.enemyStartingLocation.remove(possibleBase);
					} else {
						for (Unit u : this.game.getUnitsOnTile(possibleBase)) {
							if (u.getType().isResourceDepot())
								this.enemyStartingLocation = Collections.singletonList(possibleBase);
						}
					}
				}
			}
		}

		// iterate through my units
		for (Unit myUnit : this.self.getUnits()) {
			if (myUnit.getType().producesLarva() && this.economy.mustSpawnMoreOverlords()) {
				myUnit.train(UnitType.Zerg_Overlord);
			}

			// if there's enough minerals, train an SCV
			if (myUnit.getType().producesLarva() && this.self.minerals() - this.economy.getReservedMinerals() >= 50) {
				if (!this.unitQueue.isEmpty()) {
					myUnit.train(this.unitQueue.peek());
				}
				if (this.isTimeToDrone) {
					myUnit.train(UnitType.Zerg_Drone);
				}
			}

			if (myUnit.getType() == UnitType.Zerg_Drone && myUnit.isInterruptible() && (myUnit.isIdle() || myUnit.isGatheringMinerals() && !myUnit.isCarryingMinerals())) {
				if (this.buildingQueue.size() > 0) {
					BuildingPlan plan = this.buildingQueue.peek();
					double travelFrames = BWTA.getGroundDistance(myUnit.getTilePosition(), plan.buildingTile) / UnitType.Zerg_Drone.topSpeed();
					double expectedMinerals = this.self.minerals() + travelFrames * this.economy.getMineralIncome() / FRAME_INTERVAL_ONE;
					// TODO expectedGas
					if (expectedMinerals >= plan.building.mineralPrice() && this.self.gas() >= plan.building.gasPrice()) {
						myUnit.move(plan.buildingTile.toPosition());
						plan.builder = myUnit;
						plan.startFrame = currentFrame;
						this.activeBuildPlans.add(this.buildingQueue.poll());
					}
				}
			}

			ResearchType nextResearch = this.researchQueue.peek();
			if (nextResearch != null && myUnit.getType() == nextResearch.whatResearches()) {
				if (ResearchType.researchAtUnit(myUnit, nextResearch)) {
					this.economy.unreserveResources(nextResearch.mineralCost(), nextResearch.gasCost());
					this.researchQueue.poll();
				}
			}

			// TODO generalize scouting for all units
			if (myUnit.getType() == UnitType.Zerg_Overlord) {
				if (this.antiAirSpotted) {
					for (Unit u : myUnit.getUnitsInRadius(myUnit.getType().sightRange())) {
						if (u.getPlayer() == this.game.enemy() && u.getDistance(myUnit) <= u.getType().sightRange()) {
							this.game.drawCircleMap(u.getPosition(), u.getType().sightRange(), Color.Red);
							myUnit.move(getOppositePoint(u.getPosition(), myUnit.getPosition(), u.getType().sightRange() + 128)); // TODO shift move orthogonally?
						}
					}
				}

				if (myUnit.isIdle()) {
					if (this.enemyStartingLocation.size() > 1) {
						myUnit.move(this.enemyStartingLocation.get(0).toPosition());
						this.enemyStartingLocation.add(this.enemyStartingLocation.remove(0));
					}
				}
			}

			if (myUnit.getType().isWorker()) {
				CombatManager.manageWorkerCombat(myUnit);
				if (myUnit.isIdle()) {
					this.economy.assignMineralPatch(myUnit);
				}
			}

			if (myUnit.getType() == UnitType.Zerg_Hydralisk) {
				CombatManager.microHydralisk(myUnit);
			}
		}

		for (DrawInfo di : this.shapes) {
			if (di.pos2 != null)
				this.game.drawBoxMap(di.pos1, di.pos2, di.color, di.opaque);
			if (di.text != null)
				this.game.drawTextMap(di.pos1, di.text);
		}
		this.regions.printNames();
	}// onframe

	public static Position getOppositePoint(Position position1, Position position2, int distance) {
		int magnitude = position1.getApproxDistance(position2);
		int x = position1.getX();
		int y = position1.getY();
		return new Position((position2.getX() - x) * distance / magnitude + x, (position2.getY() - y) * distance / magnitude + y);
	}

	private int getUnitCount(UnitType type, UnitType type2) {
		int count = 0;
		for (Unit u : this.self.getUnits()) {
			if (u.getType() == type && (type2 == null || u.getBuildType() == type2)) {
				count++;
			}
		}
		return count;
	}

	private void queueBuilding(UnitType unitType, TilePosition tilePosition) {
		TilePosition buildTile = getBuildTile(unitType, tilePosition);
		if (buildTile == null) {
			System.err.println("no build tile found");
			return;
		}
		this.economy.reserveResources(unitType);
		BuildingPlan buildingPlan = new BuildingPlan(unitType, buildTile);
		if (unitType == UnitType.Zerg_Extractor) {
			buildingPlan.vespeneGeyser = this.game.getUnitsOnTile(buildTile).get(0);
		}
		this.buildingQueue.add(buildingPlan);
		this.shapes.add(new DrawInfo(buildTile.toPosition(), new TilePosition(buildTile.getX() + unitType.tileWidth(), buildTile.getY() + unitType.tileHeight()).toPosition(), Color.White, false));
	}

	private void queueResearch(ResearchType upgrade) {
		this.economy.reserveResources(upgrade.mineralCost(), upgrade.gasCost());
		this.researchQueue.add(upgrade);
	}

	private TilePosition getBuildTile(UnitType unitType, TilePosition tilePosition) {
		if (unitType.isResourceDepot()) {
			return this.economy.getNextExpansion();
		}
		if (unitType == UnitType.Zerg_Extractor) {
			return this.game.getBuildLocation(unitType, tilePosition);
		}
		TilePosition buildPosition = this.game.getBuildLocation(unitType, tilePosition, 20, true);
		// TilePosition buildPosition = new TilePosition(tilePosition.getX() -
		// unitType.tileWidth() - 1, tilePosition.getY());
		// if(!game.canBuildHere(buildPosition, unitType)) buildPosition = new
		// TilePosition(tilePosition.getX() + unitType.tileWidth() + 1,
		// tilePosition.getY());
		return buildPosition;
	}

	private void queueUnit(UnitType type, int count) {
		for (int i = 0; i < count; i++) {
			this.unitQueue.add(type);
		}
	}

	public static void main(String[] args) {
		new AlzaBot1().run();
	}
}