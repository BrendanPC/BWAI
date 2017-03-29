package SCBot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwta.BWTA;

/**
 * 
 */

/**
 * @author Brendan
 *
 */
public class Army {
	public enum ArmyStatus {
		STAGING, TRANSIT, LOSING, WINNING, RETREAT, WAITING
	}

	private List<Unit> members;
	private Position targetPosition;
	private LinkedList<Position> currentPath;
	private ArmyStatus status;
	private Game game;

	Army(Unit first, Position p, Game g) {
		members = new ArrayList<Unit>();
		members.add(first);
		targetPosition = p;
		game = g;
		status = ArmyStatus.STAGING;
		this.attackPosition(p);
	}

	private void attackPosition(Position p) {
		for (Unit u : members) {
			u.attack(p);
		}
	}

	public Position followPath() {
		if (currentPath == null || currentPath.size() < 2)
			return new Position(0,0); // just to avoid NPE on circle draw
		if (currentPath.peek().getApproxDistance(this.getArmyAverage()) < 128) {
			currentPath.pop();
		}
		this.moveTo(currentPath.peek());
		return currentPath.peek();
	}

	public void moveTo(Position p) {
		targetPosition = p;
		status = ArmyStatus.TRANSIT;
		this.attackPosition(p);
	}

	public LinkedList<Position> navigateTo(Position start, Position p) {
		LinkedList<Position> path = new LinkedList<Position>();
		int i = 0;
		for (TilePosition tp : BWTA.getShortestPath(start.toTilePosition(), p.toTilePosition())) {
			if (i > 0 && i % 10 == 0) {
				path.add(tp.toPosition());
			}
			i++;
		}
		this.currentPath = path;
		return path;
	}

	public int addUnit(Unit u) {
		members.add(u);
		u.attack(targetPosition);
		return members.size();
	}

	public int removeDeadMembers() {
		int count = 0;
		for (Unit u : members) {
			if (!u.exists())
				members.remove(u);
			else
				count++;
		}
		return count;
	}

	public Position getArmyCentre() {
		int[] xList = new int[members.size()];
		int[] yList = new int[members.size()];
		int i = 0;
		for (Unit u : members) {
			xList[i] = u.getX();
			yList[i] = u.getY();
			i++;
		}
		Arrays.sort(xList);
		Arrays.sort(yList);
		int medianX;
		int medianY;

		if (members.size() % 2 == 0) {
			medianX = (xList[members.size() / 2] + xList[members.size() / 2 + 1]) / 2;
			medianY = (yList[members.size() / 2] + yList[members.size() / 2 + 1]) / 2;
		} else {
			medianX = xList[members.size() / 2];
			medianY = xList[members.size() / 2];
		}
		return new Position(medianX, medianY);
	}

	public Position getArmyAverage() {
		int averageX = 0;
		int averageY = 0;
		for (Unit u : members) {
			averageX += u.getX();
			averageY += u.getY();
		}
		if(members.size() == 0) {
			return new Position(0,0);
		}

		return new Position(averageX / members.size(), averageY / members.size());
	}
}
