package SCBot;

import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;

public class ResearchType {
	private final UpgradeType upgradeType;
	private final TechType techType;

	public ResearchType(UpgradeType ut) {
		this.upgradeType = ut;
		this.techType = null;
	}

	public ResearchType(TechType tt) {
		this.upgradeType = null;
		this.techType = tt;
	}

	public UnitType whatResearches() {
		if (this.upgradeType != null) {
			return this.upgradeType.whatUpgrades();
		} else {
			return this.techType.whatResearches();
		}
	}
	

	public static boolean researchAtUnit(Unit unit, ResearchType rt) {
		if (rt.upgradeType != null) {
			return unit.upgrade(rt.upgradeType);
		} else {
			return unit.research(rt.techType);
		}
	}
	
	public int mineralCost() {
		if(this.upgradeType != null) {
			return this.upgradeType.mineralPrice();
		}
		else {
			return this.techType.mineralPrice();
		}
	}
	
	public int gasCost() {
		if(this.upgradeType != null) {
			return this.upgradeType.gasPrice();
		}
		else {
			return this.techType.gasPrice();
		}
	}
}
