package SCBot;

import java.util.List;

import SCBot.RegionGraph.RegionStatus;
import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Region;

public class EconomyDirector {
	public EconomyDirector(Game game) {
		this.game = game;
		this.self = game.self();
		this.reservedMinerals = 0;
		this.reservedGas = 0;
		this.currentMineralIncome = 0;
		this.currentGasIncome = 0;
		this.lastMineralsGathered = 0;
		this.lastGasGathered = 0;
		this.morphingOverlords = 1; // starting Overlord will immediately "complete" and decrement
	}

	private Game game;
	private Player self;
	private int reservedMinerals;
	private int reservedGas;

	private int currentMineralIncome;
	private int currentGasIncome;

	private int lastMineralsGathered;
	private int lastGasGathered;

	private int morphingOverlords;
	private RegionGraph regions;

	public void updateIncome() {
		if (this.game.getFrameCount() % AlzaBot1.FRAME_INTERVAL_ONE == 0) {
			this.currentMineralIncome = this.self.gatheredMinerals() - this.lastMineralsGathered;
			this.lastMineralsGathered = this.self.gatheredMinerals();
			this.currentGasIncome = this.self.gatheredGas() - this.lastGasGathered;
			this.lastGasGathered = this.self.gatheredGas();
		}
	}

	public void morphingOverlordsChange(int i) {
		this.morphingOverlords += i;
	}

	public void unreserveResources(UnitType type) {
		this.reservedMinerals -= type.mineralPrice();
		this.reservedGas -= type.gasPrice();
	}

	public void unreserveResources(int mineralCost, int gasCost) {
		this.reservedMinerals -= mineralCost;
		this.reservedGas -= gasCost;
	}

	public void reserveResources(UnitType type) {
		this.reservedMinerals += type.mineralPrice();
		this.reservedGas += type.gasPrice();
	}

	public void reserveResources(int mineralCost, int gasCost) {
		this.reservedMinerals += mineralCost;
		this.reservedGas += gasCost;
	}

	public int getReservedMinerals() {
		return this.reservedMinerals;
	}

	public int getReservedGas() {
		return this.reservedGas;
	}

	public int getMineralIncome() {
		return this.currentMineralIncome;
	}

	public int getGasIncome() {
		return this.currentGasIncome;
	}

	public boolean mustSpawnMoreOverlords() {
		return this.self.minerals() - this.reservedMinerals >= 100 && (this.self.supplyTotal() + this.morphingOverlords * 16 - this.self.supplyUsed() < 2) && this.self.supplyTotal() < 200;
	}

	public TilePosition getNextExpansion(List<AlzaBot1.BuildingPlan> currentPlans) {
		TilePosition nextExpo = null;
		Region enemyBase = this.regions.getOldestRegionWithStatus(RegionStatus.ENEMY);
		double currentValue = 0;
		for (BaseLocation b : BWTA.getBaseLocations()) {
			if (this.regions.getRegionStatus(b.getPosition()) != RegionStatus.NEUTRAL) {
				//don't want to expand to enemy, threatened or allied region
				continue;
			}
			boolean alreadyBuilding = false;
			for(AlzaBot1.BuildingPlan plan : currentPlans) {
				System.out.println(plan.buildingTile.toString() + b.getTilePosition() + (plan.buildingTile == b.getTilePosition()));
				if(plan.equalTiles(b.getTilePosition())) {
					alreadyBuilding = true;
					break;
				}
			}
			if(alreadyBuilding) {
				System.out.println("trying to build same place");
				continue;
			}
			
			double newDistance = BWTA.getGroundDistance(b.getTilePosition(), this.self.getStartLocation());
			if (newDistance < 0) {
				continue; // island expo!!
			}
			double enemyDistance = (enemyBase == null) ? 0 : BWTA.getGroundDistance(b.getTilePosition(), enemyBase.getCenter().toTilePosition());
			double baseValue = newDistance - enemyDistance;
			//System.out.println(baseValue + "to me:" + newDistance + " to them:" + enemyDistance);
			if (nextExpo == null || baseValue < currentValue) {
				nextExpo = b.getTilePosition();
				currentValue = baseValue;
			}
		}
		return nextExpo;
	}

	public void setRegions(RegionGraph regions) {
		this.regions = regions;
	}

	public boolean assignMineralPatch(Unit worker) {
		BaseLocation nearestBase = BWTA.getNearestBaseLocation(worker.getPosition());
		for(Unit potentialHatchery : this.game.getUnitsOnTile(nearestBase.getTilePosition())) {
			if(potentialHatchery.getType().isResourceDepot() && potentialHatchery.isCompleted() && nearestBase.minerals() > 0 && potentialHatchery.getPlayer() == this.self) {
				return worker.gather(nearestBase.getMinerals().get(0));
			}
		}
		// reaching here means the nearest base isn't ours or has no minerals, should be rare
		
		nearestBase = null;
		double baseDistance = 0;
		for (BaseLocation base : BWTA.getBaseLocations()) {
			for (Unit potentialHatchery : this.game.getUnitsOnTile(base.getTilePosition())) {
				if(potentialHatchery.getType().isResourceDepot() && potentialHatchery.isCompleted() && nearestBase.minerals() > 0 && potentialHatchery.getPlayer() == this.self) {
					double newDistance = BWTA.getGroundDistance(worker.getTilePosition(), base.getTilePosition());
					if(nearestBase == null || newDistance < baseDistance) {
						nearestBase = base;
						baseDistance = newDistance;
					}
				}
			}
		}
		if(nearestBase != null) {
			return worker.gather(nearestBase.getMinerals().get(0));
		}
		//TODO distance mining
		System.err.println("No mineral patches available!");
		return false;
		
	}

	public boolean shouldExpand(int buildingHatcheries) {
		//TODO need to account for both BuildingQueue and ActiveBuildPlans
		return false;
	}

}
