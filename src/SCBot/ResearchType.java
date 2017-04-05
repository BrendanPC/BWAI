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
		if (upgradeType != null) {
			return upgradeType.whatUpgrades();
		} else {
			return techType.whatResearches();
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
		if(upgradeType != null) {
			return upgradeType.mineralPrice();
		}
		else {
			return techType.mineralPrice();
		}
	}
	
	public int gasCost() {
		if(upgradeType != null) {
			return upgradeType.gasPrice();
		}
		else {
			return techType.gasPrice();
		}
	}
}
