package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.CellType;
import za.co.entelect.challenge.enums.Direction;
import za.co.entelect.challenge.enums.PowerUpType;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class Bot {

    private GameState gameState;
    private Opponent opponent;
    private MyWorm currentWorm;

    public Bot(GameState gameState) {
        this.gameState = gameState;
        this.opponent = gameState.opponents[0];
        this.currentWorm = getCurrentWorm(gameState);
    }

    // Ambil worm sekarang
    private MyWorm getCurrentWorm(GameState gameState) {
        return Arrays.stream(gameState.myPlayer.worms)
                .filter(myWorm -> myWorm.id == gameState.currentWormId)
                .findFirst()
                .get();
    }

    public Command run() {
        // Command select untuk kondisi darurat
        if (gameState.myPlayer.remainingWormSelections > 0) {
            for (int i = 0; i < gameState.myPlayer.worms.length; i++) {
                MyWorm worm = gameState.myPlayer.worms[i];

                if (worm != currentWorm && worm.health > 0 && worm.health <= 15) {
                    Cell powerPosition = powerUpPosition();
                    Position targetPos = new Position();
                    if (powerPosition != null) {
                        targetPos.x = powerPosition.x;
                        targetPos.y = powerPosition.y;
                        if (euclideanDistance(targetPos, worm.position) == 1)
                            return new SelectCommand(worm.id, forceMoveToCell(worm, powerPosition));
                    }
                }

                else if (worm.health > 0 && worm.health <= 50 && throwSkill(worm) != null) {
                    return new SelectCommand(worm.id, throwSkill(worm));
                }

                else if (worm.health > 0 && worm.health <= 25) {
                    Worm enemyWorm = getFirstWormInRange(worm);
                    if (enemyWorm != null) {
                        Direction direction = resolveDirection(worm.position, enemyWorm.position);
                        return new SelectCommand(worm.id, new ShootCommand(direction));
                    }
                }

                else if (currentWorm.health > 0 && currentWorm.health <= 15) {
                    Cell powerPosition = powerUpPosition();
                    if (powerPosition != null)
                         return new SelectCommand(currentWorm.id, forceMoveToCell(currentWorm, powerPosition));
                }

            }
        }


        // Lempar skill
        if (throwSkill(currentWorm) != null) {
            return throwSkill(currentWorm);
        }

        // Jika ada sebuah enemy worm, panggil shoot command
        Worm enemyWorm = getFirstWormInRange(currentWorm);
        if (enemyWorm != null) {
            Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
            return new ShootCommand(direction);
        }

        // Jika semua kondisi untuk perintah non-move / dig atas tidak dipenuhi
        return forceMoveToNearestEnemy(currentWorm.position);
    }

    private Command forceMoveToCell(Worm selectedWorm, Cell targetCell) {
        Position targetPosition = new Position();
        targetPosition.x = targetCell.x;
        targetPosition.y = targetCell.y;
        Direction moveDirection = resolveDirection(selectedWorm.position, targetPosition);

        // Tujuan
        int x = selectedWorm.position.x + moveDirection.x;
        int y = selectedWorm.position.y + moveDirection.y;

        List<Cell> surroundingBlocks = getSurroundingCells(selectedWorm.position.x, selectedWorm.position.y);

        for (int i = 0; i < surroundingBlocks.size(); i++) {
            Cell block = surroundingBlocks.get(i);

            if (block.x == x && block.y == y && !isOccupied(block)) {
                if (block.type == CellType.DIRT) {
                    return new DigCommand(x, y);
                } else {
                    return new MoveCommand(x, y);
                }
            }
        }

        return null;
    }

    // Gerak atau dig
    private Command forceMoveToNearestEnemy(Position currentPosition) {
        Direction moveDirection = nearestEnemyDirection(currentPosition);
        if (currentWorm.id == 3 && gameState.currentRound < 40) {
            Position randomPosition = new Position();
            Random rngGenerator = new Random();
            randomPosition.x = rngGenerator.nextInt() % 30;
            randomPosition.y = rngGenerator.nextInt() % 30;
            moveDirection = resolveDirection(currentWorm.position, randomPosition);
        }

        // Tujuan
        int x = currentWorm.position.x + moveDirection.x;
        int y = currentWorm.position.y + moveDirection.y;

        List<Cell> surroundingBlocks = getSurroundingCells(currentPosition.x, currentPosition.y);

        for (int i = 0; i < surroundingBlocks.size(); i++) {
            Cell block = surroundingBlocks.get(i);

            if (block.x == x && block.y == y && !isOccupied(block)) {
                if (block.type == CellType.DIRT) {
                    return new DigCommand(x, y);
                } else {
                    return new MoveCommand(x, y);
                }
            }
        }

        return new DoNothingCommand();
    }

    private boolean isOccupied(Cell pos) {
        for (int i = 0; i < this.opponent.worms.length; i++)
            if ((this.opponent.worms[i].position.x == pos.x) && (this.opponent.worms[i].position.y == pos.y))
                return true;
        return false;
    }

    // Cari lokasi powerup
    private Cell powerUpPosition() {
        for (int i=0; i<gameState.mapSize; i++){
            for (int j=0; j<gameState.mapSize; j++){
                if (isValidCoordinate(i, j)){
                    if(gameState.map[i][j].powerUp != null &&gameState.map[i][j].powerUp.type == PowerUpType.HEALTH_PACK){
                        return gameState.map[i][j];
                    }
                }
            }
        }
        return null;
    }

    // Ambil worm in range terdekat selectedWorm
    private Worm getFirstWormInRange(MyWorm selectedWorm) {
        Set<String> cells = constructFireDirectionLines(selectedWorm.weapon.range)
                .stream()
                .flatMap(Collection::stream)
                .map(cell -> String.format("%d_%d", cell.x, cell.y))
                .collect(Collectors.toSet());


        for (int i = 0; i < opponent.worms.length; i++) {
            boolean isLowestHP = true;
            String enemyPosition = String.format("%d_%d", opponent.worms[i].position.x, opponent.worms[i].position.y);
            if (cells.contains(enemyPosition) && opponent.worms[i].health > 0) {
                // Checking lowest hp
                for (int j = 0; j < opponent.worms.length; j++) {
                    String otherEnemyPos = String.format("%d_%d", opponent.worms[j].position.x, opponent.worms[j].position.y);
                    if (i != j && cells.contains(otherEnemyPos) && (opponent.worms[i].health > opponent.worms[j].health) && opponent.worms[j].health > 0)
                        isLowestHP = false;
                }
                if (isLowestHP)
                    return opponent.worms[i];
            }
        }

        return null;
    }

    private Direction nearestEnemyDirection(Position currentWorm) {
        int minimalDistance = 10000;
        int minimalIndex = -1;

        // Cari worm terdekat
        for (int i = 0; i < this.opponent.worms.length; i++) {
            // Cek enemy yang masih hidup, jika mati lanjutkan iterasi
            if (this.opponent.worms[i].health <= 0)
                continue;

            int temp = euclideanDistance(currentWorm, this.opponent.worms[i].position);
            if (temp < minimalDistance && this.opponent.worms[i].health > 0) {
                minimalIndex = i;
            }
        }

        return resolveDirection(currentWorm, this.opponent.worms[minimalIndex].position);
    }

    // Fungsi lempar banana bomb atau snowball
    private Command throwSkill(MyWorm selectedWorm) {
        int minimalDistance = 10000;
        int minimalIndex = -1;

        // Cari worm terdekat
        for (int i = 0; i < this.opponent.worms.length; i++) {
            int temp = euclideanDistance(selectedWorm.position.x, selectedWorm.position.y, this.opponent.worms[i].position.x, this.opponent.worms[i].position.y);

            if (temp < minimalDistance && this.opponent.worms[i].health > 0) {
                minimalIndex = i;
            }
        }

        // Apabila health worm > 50 cek apakah musuh bergerombolan. Jika iya
        // lempar banana bomb atau snowball. Apabila health <= 50, langsung lempar tanpa memikirkan
        // musuh bergerombol atau tidak
        if (euclideanDistance(selectedWorm.position, this.opponent.worms[minimalIndex].position) <= 5) {
            if (selectedWorm.snowball != null && selectedWorm.snowball.count > 0) {
                return Snowball(selectedWorm, this.opponent.worms[minimalIndex]);
            } else if (selectedWorm.bananaBomb != null && selectedWorm.bananaBomb.count > 0) {
                return BananaBomb(this.opponent.worms[minimalIndex].position);
            }
        }
        return null;
    }

    // Cek snowball
    private Command Snowball(MyWorm selectedWorm, Worm enemyWorm) {
        // Throwing snowball with conditional check
        List<Cell> surroundingBlocks = getSurroundingCells(enemyWorm.position.x, enemyWorm.position.y);
        for (int i = 0; i < surroundingBlocks.size(); i++) {
            Position targetPos = new Position();
            targetPos.x = surroundingBlocks.get(i).x;
            targetPos.y = surroundingBlocks.get(i).y;
            if (checkSnowballIsGroup(targetPos) && euclideanDistance(selectedWorm.position, targetPos) <= 5 && enemyWorm.roundsUntilUnfrozen < 2)
                return new SnowBallCommand(targetPos.x, targetPos.y);
        }

        if (enemyWorm.roundsUntilUnfrozen < 2) {
            return new SnowBallCommand(enemyWorm.position.x, enemyWorm.position.y);
        }

        return null;
    }

    // Cek banana
    private Command BananaBomb(Position enemyPosition) {
        // Throwing banana with conditional check
        if (currentWorm.health > 50 && checkBananaIsGroup(enemyPosition)) {
            return new BananaCommand(enemyPosition.x, enemyPosition.y);
        } else if (currentWorm.health <= 50) {
            return new BananaCommand(enemyPosition.x, enemyPosition.y);
        }

        return null;
    }

    // Greedy Banana
    private boolean checkBananaIsGroup (Position enemyWorm) {
        // Checking whether AOE centered on position contain multiple worm or not
        for (int i = 0; i < this.opponent.worms.length; i++) {
            if (this.opponent.worms[i].position != enemyWorm) {
                if (euclideanDistance(enemyWorm, this.opponent.worms[i].position) <= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    // Greedy Snowball
    private boolean checkSnowballIsGroup (Position enemyWorm) {
        // Checking whether AOE centered on position contain multiple worm or not
        List<Cell> surroundingBlocks = getSurroundingCells(enemyWorm.x, enemyWorm.y);
        int enemyCount = 0;

        for (int i = 0; i < surroundingBlocks.size(); i++) {
            for (int j = 0; j < this.opponent.worms.length; j++) {
                // if (this.opponent.worms[j].position != enemyWorm) {
                    if (surroundingBlocks.get(i).x == this.opponent.worms[j].position.x &&
                            surroundingBlocks.get(i).y == this.opponent.worms[j].position.y) {
                        enemyCount++;
                    }
                    if (enemyWorm.x == this.opponent.worms[j].position.x &&
                            enemyWorm.y == this.opponent.worms[j].position.y)
                        enemyCount++;
                // }
            }
        }


        if (enemyCount > 1)
            return true;
        else
            return false;
    }

    private List<List<Cell>> constructFireDirectionLines(int range) {
        // Creating Array of Cell in range of shoot
        List<List<Cell>> directionLines = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            List<Cell> directionLine = new ArrayList<>();
            for (int directionMultiplier = 1; directionMultiplier <= range; directionMultiplier++) {

                int coordinateX = currentWorm.position.x + (directionMultiplier * direction.x);
                int coordinateY = currentWorm.position.y + (directionMultiplier * direction.y);

                if (!isValidCoordinate(coordinateX, coordinateY)) {
                    break;
                }

                if (euclideanDistance(currentWorm.position.x, currentWorm.position.y, coordinateX, coordinateY) > range) {
                    break;
                }

                Cell cell = gameState.map[coordinateY][coordinateX];
                if (cell.type != CellType.AIR) {
                    break;
                }

                directionLine.add(cell);
            }
            directionLines.add(directionLine);
        }

        return directionLines;
    }

    private List<Cell> getSurroundingCells(int x, int y) {
        // Returning 8 cells around coordinate
        ArrayList<Cell> cells = new ArrayList<>();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                // Excluding search position
                // if ((i != x && j != y && isValidCoordinate(i, j)) || isValidCoordinate(i, j)) {
                if (isValidCoordinate(i, j)) {
                    cells.add(gameState.map[j][i]);
                }
            }
        }

        return cells;
    }

    private int euclideanDistance (Position a, Position b) {
        // Overloaded euclideanDistance for position arguments
        return euclideanDistance(a.x, a.y, b.x, b.y);
    }

    private int euclideanDistance(int aX, int aY, int bX, int bY) {
        // Calculate euclidean distance from 2 point rounded down to nearest integer
        return (int) (Math.sqrt(Math.pow(aX - bX, 2) + Math.pow(aY - bY, 2)));
    }

    private boolean isValidCoordinate(int x, int y) {
        // Checking whether coordinate is within map bound
        return x >= 0 && x < gameState.mapSize
                && y >= 0 && y < gameState.mapSize;
    }

    private Direction resolveDirection(Position a, Position b) {
        // Creating direction object from 2 position
        StringBuilder builder = new StringBuilder();

        int verticalComponent = b.y - a.y;
        int horizontalComponent = b.x - a.x;

        if (verticalComponent < 0) {
            builder.append('N');
        } else if (verticalComponent > 0) {
            builder.append('S');
        }

        if (horizontalComponent < 0) {
            builder.append('W');
        } else if (horizontalComponent > 0) {
            builder.append('E');
        }

        return Direction.valueOf(builder.toString());
    }
}
