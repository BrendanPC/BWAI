package SCBot;

import bwapi.Game;
import bwapi.Player;
import bwapi.UnitType;

public class EconomyDirector {
	public EconomyDirector(Game game) {
		this.game = game;
		this.self = game.self();
		reservedMinerals = 0;
		reservedGas = 0;
		currentMineralIncome = 0;
		currentGasIncome = 0;
		lastMineralsGathered = 0;
		lastGasGathered = 0;
		morphingOverlords = 1; // starting Overlord will immediately "complete" and decrement		
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
	
	public void updateIncome() {
		if (game.getFrameCount() % AlzaBot1.FRAMES_PER_CHUNK == 0) {
			currentMineralIncome = self.gatheredMinerals() - lastMineralsGathered;
			lastMineralsGathered = self.gatheredMinerals();
			currentGasIncome = self.gatheredGas() - lastGasGathered;
			lastGasGathered = self.gatheredGas();
		}
	}

	public void morphingOverlordsChange(int i) {
		morphingOverlords += i;		
	}

	public void unreserveResources(UnitType type) {
		reservedMinerals -= type.mineralPrice();
		reservedGas -= type.gasPrice();
	}
	
	public void unreserveResources(int mineralCost, int gasCost) {
		reservedMinerals -= mineralCost;
		reservedGas -= gasCost;
	}
	
	public void reserveResources(UnitType type) {
		reservedMinerals += type.mineralPrice();
		reservedGas += type.gasPrice();
	}
	
	public void reserveResources(int mineralCost, int gasCost) {
		reservedMinerals += mineralCost;
		reservedGas += gasCost;
	}
	
	public int getReservedMinerals() {
		return reservedMinerals;
	}
	
	public int getReservedGas() {
		return reservedGas;
	}
	
	public int getMineralIncome() {
		return currentMineralIncome;
	}
	
	public int getGasIncome() {
		return currentGasIncome;
	}

	public boolean mustSpawnMoreOverlords() {
		return self.minerals() - reservedMinerals >= 100 && (self.supplyTotal() + morphingOverlords * 16 - self.supplyUsed() < 2);
	}

	

	
	
	

}
