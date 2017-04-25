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
		this.members = new ArrayList<Unit>();
		this.members.add(first);
		this.targetPosition = p;
		this.game = g;
		this.status = ArmyStatus.STAGING;
		this.attackPosition(p);
	}

	private void attackPosition(Position p) {
		for (Unit u : this.members) {
			u.attack(p);
		}
	}

	public Position followPath() {
		if (this.currentPath == null || this.currentPath.size() < 2)
			return new Position(0,0); // just to avoid NPE on circle draw
		if (this.currentPath.peek().getApproxDistance(this.getArmyAverage()) < 128) {
			this.currentPath.pop();
		}
		this.moveTo(this.currentPath.peek());
		return this.currentPath.peek();
	}

	public void moveTo(Position p) {
		this.targetPosition = p;
		this.status = ArmyStatus.TRANSIT;
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
		this.members.add(u);
		u.attack(this.targetPosition);
		return this.members.size();
	}

	public int removeDeadMembers() {
		int count = 0;
		for (Unit u : this.members) {
			if (!u.exists())
				this.members.remove(u);
			else
				count++;
		}
		return count;
	}

	public Position getArmyCentre() {
		int[] xList = new int[this.members.size()];
		int[] yList = new int[this.members.size()];
		int i = 0;
		for (Unit u : this.members) {
			xList[i] = u.getX();
			yList[i] = u.getY();
			i++;
		}
		Arrays.sort(xList);
		Arrays.sort(yList);
		int medianX;
		int medianY;

		if (this.members.size() % 2 == 0) {
			medianX = (xList[this.members.size() / 2] + xList[this.members.size() / 2 + 1]) / 2;
			medianY = (yList[this.members.size() / 2] + yList[this.members.size() / 2 + 1]) / 2;
		} else {
			medianX = xList[this.members.size() / 2];
			medianY = xList[this.members.size() / 2];
		}
		return new Position(medianX, medianY);
	}

	public Position getArmyAverage() {
		int averageX = 0;
		int averageY = 0;
		for (Unit u : this.members) {
			averageX += u.getX();
			averageY += u.getY();
		}
		if(this.members.size() == 0) {
			return new Position(0,0);
		}

		return new Position(averageX / this.members.size(), averageY / this.members.size());
	}
}
