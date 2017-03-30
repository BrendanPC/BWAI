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

	public static final int FRAMES_PER_CHUNK = 500;

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
				return (self.supplyUsed() >= this.conditionCount * 2);
			case BUILDING:
				for (Unit u : self.getUnits()) {
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
			isTimeToDrone = this.continueDroning;
			if (this.effectUnitType != null) {
				if (this.effectUnitType.isBuilding()) {
					queueBuilding(this.effectUnitType, self.getStartLocation());
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
			pos1 = position;
			pos2 = position2;
			color = c;
			opaque = b;
		}

		public DrawInfo(Position position, String text) {
			pos1 = position;
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
			if (game.getFrameCount() - startFrame > 1000) {
				activeBuildPlans.remove(this); // if something has gone wrong making a building, restart the process
				buildingQueue.add(this);
			}
			return this.builder.build(this.building, this.buildingTile);
		}
	}

	public void run() {
		mirror.getModule().setEventListener(this);

		mirror.startGame();
	}

	@Override
	public void onStart() {
		game = mirror.getGame();
		self = game.self();

		buildOrder = new ArrayList<BuildStep>(20);
		buildOrder.add(new BuildStep(ConditionType.SUPPLY, 9, UnitType.Zerg_Spawning_Pool, 1, true));
		buildOrder.add(new BuildStep(ConditionType.SUPPLY, 10, UnitType.Zerg_Hatchery, 1, true));
		buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Spawning_Pool, UnitType.Zerg_Zergling, 3, true));
		buildOrder.add(new BuildStep(ConditionType.SUPPLY, 9, UnitType.Zerg_Extractor, 1, true));
		buildOrder.add(new BuildStep(ConditionType.SUPPLY, 14, UnitType.Zerg_Hydralisk_Den, 1, true));
		buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Hydralisk_Den, UnitType.Zerg_Hydralisk, 6, true));
		buildOrder.add(new BuildStep(ConditionType.BUILDING, 1, UnitType.Zerg_Hydralisk_Den, UpgradeType.Muscular_Augments, true));

		shapes = new ArrayList<DrawInfo>();

		// econ / macro
		economy = new EconomyDirector(game);
		buildingQueue = new ArrayDeque<BuildingPlan>();
		researchQueue = new ArrayDeque<ResearchType>();
		activeBuildPlans = new ArrayList<BuildingPlan>();
		unitQueue = new ArrayDeque<UnitType>();
		isTimeToDrone = true;
		currentStepNumber = 0;

		// scouting / army
		knownEnemyUnits = new HashMap<Integer, Unit>(100);
		knownEnemyBuildings = new HashMap<Integer, Unit>(100);
		stagingArmy = null;
		armies = new LinkedList<Army>();
		antiAirSpotted = false;
		CombatManager.init(game);

		game.setLocalSpeed(0);

		// Use BWTA to analyze map
		// This may take a few minutes if the map is processed first time!
		System.out.println("Analyzing map...");
		BWTA.readMap();
		BWTA.analyze();
		System.out.println("Map data ready");

		enemyStartingLocation = game.getStartLocations();
		enemyStartingLocation.remove(self.getStartLocation());

		regions = new RegionGraph(game);

		int i = 0;
		for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
			System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
			for (Position position : baseLocation.getRegion().getPolygon().getPoints()) {
				System.out.print(position + ", ");
			}
			System.out.println();
		}

		// game.sendText("black sheep wall");
		game.enableFlag(1);
	}

	public void onUnitDiscover(Unit u) {
		regions.recordUnitDiscovery(u);
		System.out.println(u.getType().toString() + u.getType().groundWeapon().maxRange());
		if (!antiAirSpotted) {
			UnitType type = u.getType();
			if (type == UnitType.Terran_Barracks || type == UnitType.Protoss_Cybernetics_Core || type == UnitType.Protoss_Photon_Cannon) {
				antiAirSpotted = true;
				// TODO zerg antiair
			}
		}
	}

	@Override
	public void onUnitCreate(Unit unit) {

	}

	public void onUnitDestroy(Unit unit) {
		regions.recordUnitDestruction(unit);
		if (unit.getPlayer() == self) {
			if (unit.getType().isBuilding())
				queueBuilding(unit.getType(), unit.getTilePosition());
			if (unit.getBuildType() == UnitType.Zerg_Overlord)
				economy.morphingOverlordsChange(-1);
		}
	}

	public void onUnitComplete(Unit unit) {
		if (unit.getPlayer() != self)
			return;
		if (unit.getType() == UnitType.Zerg_Extractor) {
			int workersOnGas = 0;
			for (Unit u : game.getUnitsInRadius(unit.getPosition(), 300)) {
				if (u.getType().isWorker() && (u.isIdle() || u.isGatheringMinerals() && !u.isCarryingMinerals())) {
					u.gather(unit);
					if (++workersOnGas >= 3)
						break;
				}
			}
		}
		if (unit.getType() == UnitType.Zerg_Overlord)
			economy.morphingOverlordsChange(-1);

		if (unit.getType() == UnitType.Zerg_Hatchery) {
			if (stagingArmy != null) {
				stagingArmy.moveTo(regions.getNewestRegionWithStatus(RegionStatus.ALLIED).getCenter());
			}
		}

		if (unit.getType() == UnitType.Zerg_Zergling || unit.getType() == UnitType.Zerg_Hydralisk) {
			if (stagingArmy == null) {
				stagingArmy = new Army(unit, regions.getNewestRegionWithStatus(RegionStatus.ALLIED).getCenter(), game);
			} else {
				if (stagingArmy.addUnit(unit) > 10) {
					stagingArmy.navigateTo(stagingArmy.getArmyAverage(), regions.getNewestRegionWithStatus(RegionStatus.ENEMY).getCenter());
					armies.add(stagingArmy);
					stagingArmy = null;
				}
			}
		}
	}

	@Override
	public void onUnitMorph(Unit unit) {
		System.out.println("FUCK " + unit.getType());
		if (unit.getPlayer() != self) {
			return;
		}
		if (unit.getType().isBuilding()) {
			regions.recordUnitDiscovery(unit);
			for (BuildingPlan plan : activeBuildPlans) {
				if (plan.builder.getID() == unit.getID() || (plan.vespeneGeyser != null && plan.vespeneGeyser.getID() == unit.getID())) {
					activeBuildPlans.remove(plan);
					economy.unreserveResources(unit.getType());
					break;
				}

			}

		}
		if (unit.getBuildType() == unitQueue.peek()) {
			unitQueue.poll();
		}
		if (unit.getBuildType() == UnitType.Zerg_Overlord) {
			economy.morphingOverlordsChange(1);
		}
	}

	@Override
	public void onFrame() {
		int currentFrame = game.getFrameCount();
		economy.updateIncome();
		game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

		BuildStep currentStep = buildOrder.get(currentStepNumber);
		if (currentStep.isConditionMet()) {
			if (currentStep.getStartFrame() == -1) {
				currentStep.setStartFrame(currentFrame);
				currentStep.followStep();
				if (buildOrder.size() > currentStepNumber + 1)
					currentStepNumber++;
			}
		}
		// if (buildOrder.size() > currentStepNumber + 1 && currentStep.isCompleted())
		// currentStepNumber++;

		regions.updateRegionStatuses();
		for (Army army : armies) {
			if (army.removeDeadMembers() == 0) {
				armies.remove(army);
			}
			game.drawCircleMap(army.followPath(), 3, Color.Cyan, true);
			game.drawCircleMap(army.getArmyAverage(), 3, Color.Green, true);
		}

		/*
		 * if (stagingArmy != null) { game.drawCircleMap(stagingArmy.followPath(), 3, Color.Cyan, true); stagingArmy.removeDeadMembers(); Position p = stagingArmy.getArmyCentre(); game.drawCircleMap(p, 3, Color.Red, true); p =
		 * stagingArmy.getArmyAverage(); game.drawCircleMap(p, 3, Color.Green, true); }
		 */
		game.drawTextScreen(10, 25, "Reserved Minerals: " + economy.getReservedMinerals() + "\nReserved Gas: " + economy.getReservedGas() + "\nCurrent Mineral Income: " + economy.getMineralIncome() + "\nCurrent Gas Income: " + economy.getGasIncome() + "\nCurrent Build Step: "
				+ currentStepNumber + "\nNext Unit: " + unitQueue.peek());

		for (BuildingPlan plan : activeBuildPlans) {
			plan.execute();
		}

		// scouting
		if (enemyStartingLocation.size() > 1) {
			for (TilePosition possibleBase : enemyStartingLocation) {
				if (game.isVisible(possibleBase)) {
					if (game.getUnitsOnTile(possibleBase).isEmpty()) {
						enemyStartingLocation.remove(possibleBase);
					} else {
						for (Unit u : game.getUnitsOnTile(possibleBase)) {
							if (u.getType().isResourceDepot())
								enemyStartingLocation = Collections.singletonList(possibleBase);
						}
					}
				}
			}
		}

		// iterate through my units
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType().producesLarva() && economy.mustSpawnMoreOverlords()) {
				myUnit.train(UnitType.Zerg_Overlord);
			}

			// if there's enough minerals, train an SCV
			if (myUnit.getType().producesLarva() && self.minerals() - economy.getReservedMinerals() >= 50) {
				if (!unitQueue.isEmpty())
					myUnit.train(unitQueue.peek());
				if (isTimeToDrone)
					myUnit.train(UnitType.Zerg_Drone);
			}

			if (myUnit.getType() == UnitType.Zerg_Drone && myUnit.isInterruptible() && (myUnit.isIdle() || myUnit.isGatheringMinerals() && !myUnit.isCarryingMinerals())) {
				if (buildingQueue.size() > 0) {
					BuildingPlan plan = buildingQueue.peek();
					double travelFrames = BWTA.getGroundDistance(myUnit.getTilePosition(), plan.buildingTile) / UnitType.Zerg_Drone.topSpeed();
					double expectedMinerals = self.minerals() + travelFrames * economy.getMineralIncome() / FRAMES_PER_CHUNK;
					//TODO expectedGas
					if (expectedMinerals >= plan.building.mineralPrice() && self.gas() >= plan.building.gasPrice()) {
						myUnit.move(plan.buildingTile.toPosition());
						plan.builder = myUnit;
						plan.startFrame = currentFrame;
						activeBuildPlans.add(buildingQueue.poll());
					}
				}
			}

			ResearchType nextResearch = researchQueue.peek();
			if (nextResearch != null && myUnit.getType() == nextResearch.whatResearches()) {
				if (ResearchType.researchAtUnit(myUnit, nextResearch)) {
					economy.unreserveResources(nextResearch.mineralCost(), nextResearch.gasCost());
					researchQueue.poll();
				}
			}

			// TODO generalize scouting for all units
			if (myUnit.getType() == UnitType.Zerg_Overlord) {
				if (antiAirSpotted) {
					for (Unit u : myUnit.getUnitsInRadius(myUnit.getType().sightRange())) {
						if (u.getPlayer() == game.enemy() && u.getDistance(myUnit) <= u.getType().sightRange()) {
							game.drawCircleMap(u.getPosition(), u.getType().sightRange(), Color.Red);
							myUnit.move(getOppositePoint(u.getPosition(), myUnit.getPosition(), u.getType().sightRange() + 128)); // TODO shift move orthogonally?
						}
					}
				}

				if (myUnit.isIdle()) {
					if (enemyStartingLocation.size() > 1) {
						myUnit.move(enemyStartingLocation.get(0).toPosition());
						enemyStartingLocation.add(enemyStartingLocation.remove(0));
					}
				}
			}

			if (myUnit.getType().isWorker()) {

				CombatManager.manageWorkerCombat(myUnit);

				if (myUnit.isIdle()) {
					Unit closestMineral = getClosestMineralPatch(myUnit);
					// if a mineral patch was found, send the worker to gather it
					if (closestMineral != null) {
						myUnit.gather(closestMineral, false);
					}
				}
			}

			if (myUnit.getType() == UnitType.Zerg_Hydralisk) {
				CombatManager.microHydralisk(myUnit);
			}
		}

		// macro!
		if (self.minerals() - economy.getReservedMinerals() > 300 && economy.getMineralIncome() > 200) {
			queueBuilding(UnitType.Zerg_Hatchery, self.getStartLocation());
		}

		for (DrawInfo di : shapes) {
			if (di.pos2 != null)
				game.drawBoxMap(di.pos1, di.pos2, di.color, di.opaque);
			if (di.text != null)
				game.drawTextMap(di.pos1, di.text);
		}
		regions.printNames();
	}// onframe

	private Unit getClosestMineralPatch(Unit worker) {
		for (BaseLocation base : BWTA.getBaseLocations()) {
			for (Unit hatchery : game.getUnitsOnTile(base.getTilePosition())) {
				if (hatchery.getType().producesLarva() && hatchery.getPlayer() == self) {
					// base is mine
					int drones = 0;
					for (Unit u : hatchery.getUnitsInRadius(320)) {
						if (u.getType().isWorker())
							drones++;
					}
					if (drones < (base.isMineralOnly() ? 10 : 12)) {
						if (!base.getMinerals().isEmpty())
							return base.getMinerals().get(0);
					}
				}
			}
		}
		return null;
	}

	public static Position getOppositePoint(Position position1, Position position2, int distance) {
		int magnitude = position1.getApproxDistance(position2);
		int x = position1.getX();
		int y = position1.getY();
		return new Position((position2.getX() - x) * distance / magnitude + x, (position2.getY() - y) * distance / magnitude + y);
	}

	

	private int getUnitCount(UnitType type, UnitType type2) {
		int count = 0;
		for (Unit u : self.getUnits()) {
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
		economy.reserveResources(unitType);
		BuildingPlan buildingPlan = new BuildingPlan(unitType, buildTile);
		if (unitType == UnitType.Zerg_Extractor) {
			buildingPlan.vespeneGeyser = game.getUnitsOnTile(buildTile).get(0);
		}
		buildingQueue.add(buildingPlan);
		shapes.add(new DrawInfo(buildTile.toPosition(), new TilePosition(buildTile.getX() + unitType.tileWidth(), buildTile.getY() + unitType.tileHeight()).toPosition(), Color.White, false));
	}

	private void queueResearch(ResearchType upgrade) {
		economy.reserveResources(upgrade.mineralCost(), upgrade.gasCost());
		researchQueue.add(upgrade);
	}

	private TilePosition getBuildTile(UnitType unitType, TilePosition tilePosition) {
		if (unitType.isResourceDepot()) {
			return getNextExpansion();
		}
		if (unitType == UnitType.Zerg_Extractor) {
			return game.getBuildLocation(unitType, tilePosition);
		}
		TilePosition buildPosition = game.getBuildLocation(unitType, tilePosition, 20, true);
		// TilePosition buildPosition = new TilePosition(tilePosition.getX() -
		// unitType.tileWidth() - 1, tilePosition.getY());
		// if(!game.canBuildHere(buildPosition, unitType)) buildPosition = new
		// TilePosition(tilePosition.getX() + unitType.tileWidth() + 1,
		// tilePosition.getY());
		return buildPosition;
	}

	private void queueUnit(UnitType type, int count) {
		for (int i = 0; i < count; i++) {
			unitQueue.add(type);
		}
	}

	private TilePosition getNextExpansion() {
		TilePosition nextExpo = null;
		double currentDistance = 0;
		for (BaseLocation b : BWTA.getBaseLocations()) {
			double newDistance = BWTA.getGroundDistance(b.getTilePosition(), self.getStartLocation());
			if (newDistance < 0)
				continue; // island expo!!
			if (nextExpo == null || newDistance < currentDistance) {
				boolean alreadyOwned = false;
				for (Unit u : game.getUnitsOnTile(b.getTilePosition())) {
					if (u.getType().producesLarva() && u.getPlayer() == self)
						alreadyOwned = true;
				}
				if (alreadyOwned)
					continue;
				nextExpo = b.getTilePosition();
				currentDistance = newDistance;
			}
		}
		return nextExpo;
	}

	public static void main(String[] args) {
		new AlzaBot1().run();
	}
}