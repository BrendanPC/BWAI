package SCBot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import bwapi.Color;
import bwapi.Game;
import bwapi.Pair;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.Chokepoint;
import bwta.Region;

public class RegionGraph {
	public enum RegionStatus {
		ALLIED, ENEMY, NEUTRAL, THREATENED
	}

	private Game game;
	private HashMap<String, Node> nodes; // id -> node
	private ArrayList<Chokepoint> chokes;

	private class Node implements Comparable<Node> {
		private String id;
		private Region region;
		private RegionStatus status;
		private HashMap<String, Double> adjacent; // id -> distance
		private HashMap<Integer, Unit> enemyBuildings; // TODO do I need to store Unit? could just be Set of Ints
		private int alliedBuildings;
		private int lastUpdate;

		Node(Region r) {
			this.region = r;
			this.status = RegionStatus.NEUTRAL;
			this.adjacent = new HashMap<String, Double>();
			enemyBuildings = new HashMap<Integer, Unit>();
			alliedBuildings = 0;
		}

		public void addEdge(String neighbour, double distance) {
			this.adjacent.put(neighbour, distance);
		}

		@Override
		public int compareTo(Node o) {
			return Integer.compare(o.lastUpdate, this.lastUpdate);
		}
	}

	public RegionGraph(Game g) {
		this.game = g;
		this.nodes = new HashMap<String, Node>();
		this.chokes = new ArrayList<Chokepoint>(BWTA.getChokepoints());
		int i = 0;
		for (Region r : BWTA.getRegions()) {
			nodes.put(r.getCenter().toString(), new Node(r));
		}
		for (Chokepoint c : chokes) {
			Pair<Region, Region> pair = c.getRegions();
			String firstId = generateId(pair.first);
			String secondId = generateId(pair.second);
			double distance = BWTA.getGroundDistance(pair.first.getCenter().toTilePosition(), pair.second.getCenter().toTilePosition());
			nodes.get(firstId).addEdge(secondId, distance);
			nodes.get(secondId).addEdge(firstId, distance);
		}
	}

	public void printNames() {
		for (Node n : nodes.values()) {
			game.drawTextMap(n.region.getCenter(), n.status.toString() + Integer.toString(n.enemyBuildings.size()));
		}
		for (Chokepoint c : chokes) {
			Pair<Region, Region> pair = c.getRegions();
			game.drawLineMap(pair.first.getCenter(), pair.second.getCenter(), Color.Green);
		}
	}

	public static String generateId(Region r) {
		return r.getCenter().toString();
	}

	public void setStatus(Position position, RegionStatus status) {
		nodes.get(generateId(BWTA.getRegion(position))).status = status;
	}

	public RegionStatus getRegionStatus(Position position) {
		return nodes.get(generateId(BWTA.getRegion(position))).status;
	}

	public ArrayList<Region> getRegionsWithStatus(RegionStatus status) {
		Node[] sorted = (Node[]) nodes.values().toArray();
		Arrays.sort(sorted);
		ArrayList<Region> regions = new ArrayList<Region>(sorted.length);
		for (Node n : sorted) {
			if (n.status == status)
				regions.add(n.region);
		}
		return regions;
	}

	public Region getNewestRegionWithStatus(RegionStatus status) {
		int age = 0;
		Region r = null;
		for (Node n : nodes.values()) {
			if (n.status == status && n.lastUpdate > age) {
				age = n.lastUpdate;
				r = n.region;
			}
		}
		return r;
	}

	public void updateRegionStatuses() {
		if (game.getFrameCount() % AlzaBot1.FRAMES_PER_CHUNK != 0) {
			return;
		}
		for (Node node : nodes.values()) {
			if (node.status != RegionStatus.THREATENED) {
				continue;
			}
			boolean foundEnemy = false;
			for (Unit u : game.getUnitsInRadius(node.region.getCenter(), 1000)) {
				if (u.getPlayer() == game.enemy()) {
					foundEnemy = true;
					break;
				}
			}
			if (!foundEnemy) {
				node.lastUpdate = game.getFrameCount();
				node.status = RegionStatus.ALLIED;
			}
		}
	}

	public RegionStatus recordUnitDiscovery(Unit unit) {
		Node node = nodes.get(generateId(BWTA.getRegion(unit.getPosition())));
		node.lastUpdate = game.getFrameCount();
		if (unit.getPlayer() == game.enemy() && unit.getType().isBuilding() && !node.enemyBuildings.containsKey(unit.getID())) {
			node.enemyBuildings.put(unit.getID(), unit);
			switch (node.status) {
			case ALLIED:
				node.status = RegionStatus.THREATENED;
				break;
			case ENEMY:
			case THREATENED:
			case NEUTRAL:
				node.status = RegionStatus.ENEMY;
			}
		}
		if (unit.getPlayer() == game.self() && unit.getType().isBuilding()) {
			node.alliedBuildings++;
			switch (node.status) {
			case ENEMY:
				node.status = RegionStatus.THREATENED;
				break;
			case ALLIED:
			case THREATENED:
			case NEUTRAL:
				node.status = RegionStatus.ALLIED;
			}
		}
		return node.status;
	}

	public RegionStatus recordUnitDestruction(Unit unit) {
		Node node = nodes.get(generateId(BWTA.getRegion(unit.getPosition())));
		node.lastUpdate = game.getFrameCount();
		if (unit.getPlayer() == game.enemy() && unit.getType().isBuilding()) {
			node.enemyBuildings.remove(unit.getID());
			if (node.enemyBuildings.isEmpty()) {
				node.status = RegionStatus.NEUTRAL;
			}
		}
		if (unit.getPlayer() == game.self()) {
			if (node.status == RegionStatus.ALLIED)
				node.status = RegionStatus.THREATENED;
			if (unit.getType().isBuilding()) {
				if (--node.alliedBuildings < 1)
					node.status = RegionStatus.NEUTRAL;
			}
		}
		return node.status;
	}

}
