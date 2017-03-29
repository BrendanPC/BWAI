package SCBot;

import java.util.ArrayList;

import bwapi.Game;
import bwapi.Order;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;

public class CombatManager {
	private static Game game;

	public static void init(Game g) {
		game = g;
	}

	public enum TargetPriority {
		LOW(0), MEDIUM(1), HIGH(2);
		private final int level;

		TargetPriority(int lvl) {
			level = lvl;
		}
	}
	
	public static TargetPriority getTargetPriority(UnitType type) {
		if(type == null || type.isWorker() || (type.isBuilding() && !type.canAttack())) {
			return TargetPriority.LOW;
		}
		
		if(type == UnitType.Terran_Siege_Tank_Siege_Mode || type == UnitType.Terran_Siege_Tank_Tank_Mode || type == UnitType.Protoss_Reaver || type == UnitType.Protoss_Archon) {
			return TargetPriority.HIGH;
		}
		
		return TargetPriority.MEDIUM;
	}

	public static void manageWorkerCombat(Unit worker) {

		if (worker.getOrder() == Order.AttackUnit && game.getFrameCount() - worker.getLastCommandFrame() > 80) {
			worker.stop();
		}
		// Worker Combat!
		if (worker.isUnderAttack()) {
			Unit escapeMineral = null;
			int mineralDistance = 0;
			for (Unit m : BWTA.getNearestBaseLocation(worker.getPosition()).getMinerals()) {
				if (m.getDistance(worker) > mineralDistance) {
					mineralDistance = m.getDistance(worker);
					escapeMineral = m;
				}
			}
			int attackers = 0;
			Unit attacker = null;
			ArrayList<Unit> defenders = new ArrayList<Unit>();
			defenders.add(worker);
			for (Unit u : worker.getUnitsInRadius(100)) {
				if (u.getPlayer() == game.enemy()) {
					attacker = u;
					attackers++;
				}
				if (u.getPlayer() == game.self() && !u.getType().isBuilding()) {
					defenders.add(u);
				}
			}
			int defenderCount = 0;
			for (Unit defender : defenders) {
				if (defender.getHitPoints() <= (attackers > 2 ? 19 : 10)) {
					System.out.println("Retreating! " + defender.getID());
					if (escapeMineral != null) {
						defender.gather(escapeMineral);
					} else {
						// TODO run away!
					}
				} else {
					System.out.println("Launching attack! " + defender.getID());
					if (attacker != null)
						if (defenderCount < attackers * 3) {
							defender.attack(attacker);
							defenderCount++;
						}
				}
			}
		}
	}

	public static void microHydralisk(Unit hydralisk) {
		if(hydralisk.getType() != UnitType.Zerg_Hydralisk) return;
		int weaponCooldown = hydralisk.getGroundWeaponCooldown();
		double ownSpeed = game.self().topSpeed(hydralisk.getType());
		boolean meleePresent = false;
		double meleeSpeed = 0;
		int targetHP = 1000;
		Unit target = null;
		int enemies = 0;
		int averageX = 0;
		int averageY = 0;
		for(Unit u: hydralisk.getUnitsInRadius(UnitType.Zerg_Hydralisk.groundWeapon().maxRange())) {
			if(u.getPlayer() != game.enemy()) continue;
			enemies++;
			averageX += u.getX();
			averageY += u.getY();
			if(!meleePresent && isMelee(u.getType())) {
				meleePresent = true;
				meleeSpeed = game.enemy().topSpeed(u.getType());
			}
			int hp = u.getHitPoints() + u.getShields();
			if(target == null || (hp < targetHP && getTargetPriority(target.getType()).level <= getTargetPriority(u.getType()).level)) {
				target = u;
				targetHP = hp;
			}
		}
		if(target == null) return;
		if(weaponCooldown < 6 && (!meleePresent || meleeSpeed > ownSpeed || hydralisk.getDistance(target) > 20)) hydralisk.attack(target);
		else hydralisk.move(AlzaBot1.getOppositePoint(hydralisk.getPosition(), target.getPosition(), (meleePresent ? -128 : 64)));
	}

	public static boolean isMelee(UnitType type) {
		return type.groundWeapon().maxRange() <= 32;
	}
}
