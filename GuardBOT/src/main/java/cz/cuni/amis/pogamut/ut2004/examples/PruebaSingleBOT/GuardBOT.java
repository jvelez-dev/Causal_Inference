package cz.cuni.amis.pogamut.ut2004.examples.PruebaSingleBOT;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;

import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathExecutorState;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.IUT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathAutoFixer;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathExecutorStuckState;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.astar.UT2004AStar;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.floydwarshall.FloydWarshallMap;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshModule;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Self;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;
import cz.cuni.amis.utils.flag.FlagListener;

/**
 * Example of Simple Pogamut bot, that randomly walks around the map. 
 * 
 * <p><p> 
 * Bot is able to handle movers as well as teleporters. 
 * 
 * <p><p> 
 * It also implements player-following, that is, if it sees a player, 
 * it will start to navigate to it.
 * 
 * <p><p>
 * We recommend you to try it on map DM-1on1-Albatross or CTF-LostFaith or DM-Flux2.
 * 
 * <p><p>
 * This bot also contains an example of {@link TabooSet} usage.
 * 
 * <p><p>
 * Bot also instantiates {@link UT2004PathAutoFixer} that automatically removes bad-edges
 * from navigation graph of UT2004. Note that Pogamut bot's cannot achieve 100% safe navigation
 * inside UT2004 maps mainly due to edges that does not contain enough information on how
 * to travel them, we're trying our best, but some edges inside navigation graph exported
 * from UT2004 cannot be traveled with our current implementation.
 * 
 * <p><p>
 * You may control the way the bot informs you about its decisions via {@link #shouldLog} and {@link #shouldSpeak} flags.
 * 
 * <p><p>
 * We advise to change chat settings within UT2004 via ESCAPE -> Settings -> HUD -> Max. Chat Count -> set to 8 (max).
 *
 * @author Rudolf Kadlec aka ik
 * @author Jakub Gemrot aka Jimmy
 */
@AgentScoped
public class GuardBOT extends UT2004BotModuleController {

    /**
     * Taboo set is working as "black-list", that is you might add some
     * NavPoints to it for a certain time, marking them as "unavailable".
     */
    protected TabooSet<NavPoint> tabooNavPoints;
    
    /**
     * Current navigation point we're navigating to.
     */
    protected NavPoint targetNavPoint;
    
    /**
     * Path auto fixer watches for navigation failures and if some navigation
     * link is found to be unwalkable, it removes it from underlying navigation
     * graph.
     *
     * Note that UT2004 navigation graphs are some times VERY stupid or contains
     * VERY HARD TO FOLLOW links...
     */
    protected UT2004PathAutoFixer autoFixer;
    
    /**
     * Standard {@link UT2004BotModuleController#getNavigation()} is using {@link FloydWarshallMap} to find the path.
     * <p><p>
     * This {@link UT2004Navigation} is initialized using {@link UT2004BotModuleController#getAStar()} and can be used to confirm, that
     * {@link UT2004AStar} is working in the map.
     */
    protected UT2004Navigation navigationAStar;
    
    /**
     * {@link NavigationBot#talking} state.
     */
    protected int talking;
    
    /**
     * Whether to use {@link #navigationAStar} and {@link UT2004AStar} (== true).
     * <p><p>
     * Can be configured from NetBeans plugin during runtime.
     */
    @JProp
    public boolean useAStar = false;
    
    /**
     * Whether to use {@link #nmNav} or standard {@link UT2004BotModuleController#getNavigation()}.
     * <p><p>
     * Can be configured from NetBeans plugin during runtime.
     * <p><p>
     * Note that you must have corresponding .navmesh file for a current played map within directory ./navmesh, more info available at {@link NavMeshModule}.
     * <p><p>
     * Note that navigation bot comes with only three navmeshes DM-TrainingDay, DM-1on1-Albatross and DM-Flux2 (see ./navmesh folder within the project folder).
     */
    @JProp
    public boolean useNavMesh = false;
    
    /**
     * Whether we should draw the navmesh before we start running using {@link #nmNav} or standard {@link UT2004BotModuleController#getNavigation()}.
     * <p><p>
     * Can be configured from NetBeans plugin during runtime.
     */
    @JProp
    public boolean drawNavMesh = true;

    /**
     * Whether we should speak using in game communication within {@link #say(String)}.
     */
    public boolean shouldSpeak = true;
    
    /**
     * Whether to LOG messages within {@link #say(String)}.
     */
    public boolean shouldLog = false;
    
    /**
     * What log level to use.  
     */
    public Level navigationLogLevel = Level.WARNING;
    
    /**
     * Here we will store either {@link UT2004BotModuleController#getNavigation()} or {@link #navigationAStar} according to {@link #useAStar}.
     */
    protected IUT2004Navigation navigationToUse;

	private boolean navMeshDrawn = false;

	private int waitForMesh;

	private double waitingForMesh;

	private boolean offMeshLinksDrawn = false;

	private int waitForOffMeshLinks;

	private double waitingForOffMeshLinks;
	private int sentidos;

    /**
     * Here we can modify initializing command for our bot.
     *
     * @return
     */
    @Override
    public Initialize getInitializeCommand() {
        return new Initialize().setName("Guard_BOT").setDesiredSkill(7).setLocation(getNavPoints().getNavPoint("Test_Environment.PathNode74").getLocation());
    }
    
    @Override
    public void mapInfoObtained() {
    	// YOU CAN USE navBuilder IN HERE
    	
    	// IN WHICH CASE YOU SHOULD UNCOMMENT FOLLOWING LINE AFTER EVERY CHANGE
    	navMeshModule.setReloadNavMesh(true); // tells NavMesh to reconstruct OffMeshPoints    	
    }

    /**
     * The bot is initialized in the environment - a physical representation of
     * the bot is present in the game.
     *
     * @param config information about configuration
     * @param init information about configuration
     */
    @SuppressWarnings("unchecked")
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange config, InitedMessage init) {
        // initialize taboo set where we store temporarily unavailable navpoints
        tabooNavPoints = new TabooSet<NavPoint>(bot);

        // auto-removes wrong navigation links between navpoints
        autoFixer = new UT2004PathAutoFixer(bot, navigation.getPathExecutor(), fwMap, aStar, navBuilder);

        // IMPORTANT
        // adds a listener to the path executor for its state changes, it will allow you to 
        // react on stuff like "PATH TARGET REACHED" or "BOT STUCK"
        navigation.getPathExecutor().getState().addStrongListener(new FlagListener<IPathExecutorState>() {

            @Override
            public void flagChanged(IPathExecutorState changedValue) {
                pathExecutorStateChange(changedValue);
            }
        });
        
        nmNav.getPathExecutor().getState().addStrongListener(new FlagListener<IPathExecutorState>() {

            @Override
            public void flagChanged(IPathExecutorState changedValue) {
                pathExecutorStateChange(changedValue);
            }
        });
        
        navigationAStar = new UT2004Navigation(bot, navigation.getPathExecutor(), aStar, navigation.getBackToNavGraph(), navigation.getRunStraight());          
        navigationAStar.getLog().setLevel(navigationLogLevel);
        
        navigation.getLog().setLevel(navigationLogLevel);
        
        nmNav.setLogLevel(navigationLogLevel);
        move.turnTo(getNavPoints().getNavPoint("Test_Environment.PathNode75").getLocation());  
        try {
            File file = new File("C:\\sentidos.txt");
                Scanner sc = new Scanner(file);     
                while (sc.hasNextLine())
                    sentidos=Integer.parseInt(sc.nextLine());
                sc.close();
            }catch (Exception e) {
    			// TODO: handle exception
    		}
    }

    /**
     * The bot is initialized in the environment - a physical representation of
     * the bot is present in the game.
     *
     * @param config information about configuration
     * @param init information about configuration
     */
    @Override
    public void botFirstSpawn(GameInfo gameInfo, ConfigChange config, InitedMessage init, Self self) {
        // receive logs from the navigation so you can get a grasp on how it is working
        //navigation.getPathExecutor().getLog().setLevel(Level.ALL);
    	//nmNav.setLogLevel(Level.ALL);
    	//navigationAStar.getPathExecutor().getLog().setLevel(Level.ALL);

    }

    /**
     * This method is called only once right before actual logic() method is
     * called for the first time.
     */
    @Override
    public void beforeFirstLogic() {
    }

    /**
     * Main method that controls the bot - makes decisions what to do next. It
     * is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second - it
     * is affected by visionTime variable, that can be adjusted in GameBots ini
     * file in UT2004/System folder.
     */

    public <E> List<E> collectionToList(Collection<E> collection)    {
        List<E> list;
        if (collection instanceof List)        {
            list = (List<E>) collection;
        }
        else        {
            list = new ArrayList<E>(collection);
        }
        return list;
    }
  @Override
  public void logic() {
	  if(sentidos == 1) {
		  Player player = players.getNearestVisiblePlayer();
		  if (player != null) {
			  talkTo(player);
			  return;
		  }
      }
	  if(sentidos == 2) {		  
		  if(getSenses().isHearingNoise()) {
			  body.getLocomotion().turnTo(getSenses().getNoiseRotation().toLocation());
			  Player player = players.getNearestVisiblePlayer();
			  if (player != null) {
				  talkTo(player);
				  return;
			  }
		  }
      }    
	  if(sentidos==3) {
		  if(getSenses().isHearingNoise()) {
			  body.getLocomotion().turnTo(getSenses().getNoiseRotation().toLocation());
		  }
	  }
  }

    

    private void talkTo(Player player) {
        // FACE THE PLAYER
        move.turnTo(player);
        if(player.getLocation().equals(bot.getLocation())) {
        	body.getCommunication().sendGlobalTextMessage(""+player.getName()+"¡LO LOGRASTE!");
        }
        else {
        	body.getCommunication().sendGlobalTextMessage(""+player.getName()+"¡ESTAS FUERA!");
        }
    }


	private void say(String text) {
		if (shouldSpeak) { 
			body.getCommunication().sendGlobalTextMessage(text);
		}
		if (shouldLog) {
			say(text);
		}
	}

    /**
     * Called each time our bot die. Good for reseting all bot state dependent
     * variables.
     *
     * @param event
     */
    @Override
    public void botKilled(BotKilled event) {
        navigation.stopNavigation();
    }

    /**
     * Path executor has changed its state (note that {@link UT2004BotModuleController#getPathExecutor()}
     * is internally used by
     * {@link UT2004BotModuleController#getNavigation()} as well!).
     *
     * @param event
     */
    protected void pathExecutorStateChange(IPathExecutorState event) {
        switch (event.getState()) {
            case PATH_COMPUTATION_FAILED:

                break;

            case TARGET_REACHED:

                break;

            case STUCK:
            	
                break;

            case STOPPED:

                break;
        }
    }


    protected NavPoint getGuardingNavPoint() {
    	List<NavPoint> lstpts = collectionToList(getWorldView().getAll(NavPoint.class).values());
  	  	for(NavPoint p:lstpts) {
  		  if(p.getId().toString().equals("WorldObjectId[Test_Environment.PathNode74]")) {
  			  return(p);
  		  }
  	  }
  	  	return(null);
    }

    private NavPoint getPosicion(String id) {
    	List<NavPoint> lstpts = collectionToList(getWorldView().getAll(NavPoint.class).values());
  	  	for(NavPoint p:lstpts) {
  		  if(p.getId().toString().equals(id)) {
  			  return(p);
  		  }
  	  }
  	  	return(null);
    }
    public static void main(String args[]) throws PogamutException {
        // wrapped logic for bots executions, suitable to run single bot in single JVM

        // you can set the log level to FINER to see (almost) all logs 
        // that describes decision making behind movement of the bot as well as incoming environment events
		// however note that in NetBeans this will cause your bot to lag heavilly (in Eclipse it is ok)
        new UT2004BotRunner(GuardBOT.class, "Guard_BOT").setMain(true).setLogLevel(Level.WARNING).startAgent();
    }
}


