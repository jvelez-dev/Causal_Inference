package cz.cuni.amis.pogamut.ut2004.examples.PruebaSingleBOT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;

import cz.cuni.amis.introspection.IntrospectionException;
import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathExecutorState;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.IUT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004Navigation;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.UT2004PathAutoFixer;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.astar.UT2004AStar;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.floydwarshall.FloydWarshallMap;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.navmesh.NavMeshModule;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GlobalChat;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPointNeighbourLink;
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
public class CausalBOT extends UT2004BotModuleController {

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
     * {@link NavigationBot2#talking} state.
     */
    protected int talking;
    
    /**
     * Whether to use {@link #navigationAStar} and {@link UT2004AStar} (== true).
     * <p><p>
     * Can be configured from NetBeans plugin during runtime.
     */
    @JProp
    public boolean useAStar = true;
    
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
    public boolean drawNavMesh = false;

    @JProp
    public ArrayList<NavPoint> configuredPath = new ArrayList<NavPoint>();
    
    @JProp
    public int healthLevel=50;
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

	public ArrayList<ArrayList<Integer>> caminosALL = new ArrayList<ArrayList<Integer>>();
	public Map<String,Integer> mapa = new HashMap<String,Integer>();
	public int i;
  	int camino_1[] = {41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74};
  	int camino_2[] = {41,42,43,44,45,46,47,48,49,76,77,78,79,80,92,93,94,95,100,119,120,121,122,123,124,125,75};
	int camino_3[] = {88,89,90,91,87,86,85,105,106,107,108,109,110,111,112,113,114,115,116,117,118,124,125,75};
	int camino_4[] = {88,89,90,91,87,86,85,105,106,107,108,109,104,103,102,101,100,119,120,121,122,123,124,125,75};
	int camino_5[] = {88,89,90,91,87,86,85,84,83,82,81,80,92,93,94,95,100,119,120,121,122,123,124,125,75};
	int camino_6[] = {41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,96,97,98,99,132,100,119,120,121,122,123,124,125,75};
	int camino_7[] = {41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,126,127,128,129,130,131,124,125,75};
	int camino[];
	int c;
    /**
     * Here we can modify initializing command for our bot.
     *
     * @return
     */
    @Override
    public Initialize getInitializeCommand() {
        return new Initialize().setName("Causal_BOT");
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
        navigationAStar = new UT2004Navigation(bot, navigation.getPathExecutor(), aStar, navigation.getBackToNavGraph(), navigation.getRunStraight());          
        navigationAStar.getLog().setLevel(navigationLogLevel);

        navigation.getLog().setLevel(Level.INFO);          
        
        //Random rn = new Random();
        //c = rn.nextInt(7 - 1 + 1) + 1;
        int c=0;
        try {
        File file = new File("C:\\camino.txt");
            Scanner sc = new Scanner(file);     
            while (sc.hasNextLine())
                c=Integer.parseInt(sc.nextLine());
            sc.close();
        }catch (Exception e) {
			// TODO: handle exception
		}
        switch(c) {
        case 1: camino = camino_1; break;
        case 2: camino = camino_2; break;
        case 3: camino = camino_3; break;
        case 4: camino = camino_4; break;
        case 5: camino = camino_5; break;
        case 6: camino = camino_6; break;
        case 7: camino = camino_7; break;
        default: camino = camino_1; break;
        }    
        System.out.println("Voy a recorrer el camino porque es el de mejor probabilidad"+c);
        
//        c=4;
//       camino=camino_4;
       i=0;
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
    	navigationAStar.getPathExecutor().getLog().setLevel(Level.ALL);    
    }

    /**
     * This method is called only once right before actual logic() method is
     * called for the first time.
     */
    @Override
    public void beforeFirstLogic() {    	
  	  	configuredPath.add(buscarNavPoint("Test_Environment.PlayerStart3"));
  	  	for(int i=0; i<camino.length;i++) {
  	  		configuredPath.add(buscarNavPoint("Test_Environment.PathNode"+camino[i]));  	  		
  	  	}
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
  	  if(navigation.isNavigating()) {
  		return;  
  	  }
  	  if(i<configuredPath.size()) {
  		  targetNavPoint = configuredPath.get(i);
  		  i+=1;
  		  navigation.navigate(targetNavPoint);
  		  System.out.println("Pasando por: "+camino[i]);
  	  } 	  	  
    }
    
    
    @EventListener(eventClass=GlobalChat.class)
    public void chatReceived(GlobalChat msg) throws IntrospectionException {    	
    	if(msg.getText().equals("Explorer_BOT¡ESTAS FUERA!")) {
    		say("Oh Noo!");    		
    		//int nodoDetectado = Integer.parseInt(targetNavPoint.getId().getStringId().substring(25));
    		//String registro =""+bot.getName()+";"+Arrays.toString(camino)+";"+(camino.length-Arrays.binarySearch(camino, nodoDetectado))+"/"+camino.length+";Luz;"+"Fracaso";
    		/*Guarda:
    		 * BOT
    		 * Camino [1,2,3 o 4]
    		 * Porcentaje de exito por ese camino
    		 * Iluminación [0 no hay] [1 sí hay]
    		 * Resultado [0 fracaso] [1 exito]
    		*/
    		double porcentaje = (i*100)/camino.length;	    		
    		String registro =""+bot.getName()+";"+c+";"+(i-1)+";"+camino.length+";"+String.format("%,.2f", porcentaje)+";1;"+"0";	    		
    		guardar(registro);
    		bot.kill();    		    
    	}
    }   

    public void guardar(String arr) {
    	BufferedWriter bw = null;

        try {
        	bw = new BufferedWriter(new FileWriter("c:\\registro.txt", true));
        	bw.write(arr);
        	bw.newLine();
        	bw.flush();
        } catch (IOException ioe) {
  	 ioe.printStackTrace();
        } finally {                       // always close the file
  	 if (bw != null) try {
  	    bw.close();
  	 } catch (IOException ioe2) {
  	    // just ignore it
  	 }        } // end try/catch/finally
    }
    
	@Override
	public void botShutdown() {

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

    }

    /**
     * Path executor has changed its state (note that {@link UT2004BotModuleController#getPathExecutor()}
     * is internally used by
     * {@link UT2004BotModuleController#getNavigation()} as well!).
     *
     * @param event
     */
    protected void pathExecutorStateChange(IPathExecutorState event) {
    	double porcentaje=0.0;
    	String registro="";
        switch (event.getState()) {
            case PATH_COMPUTATION_FAILED:
                // if path computation fails to whatever reason, just try another navpoint
                // taboo bad navpoint for 3 minutes
                //tabooNavPoints.add(targetNavPoint, 180);
                break;

            case TARGET_REACHED:
            	say("Punto Alcanzado");
            	if(i==configuredPath.size()) {
          		  getBody().getLocomotion().stopMovement();
          		  say("Lo logré!");
          		  porcentaje = (i*100)/camino.length;	    		
          		  registro =""+bot.getName()+";"+c+";"+(i-1)+";"+camino.length+";"+String.format("%,.2f", porcentaje)+";1;"+"1";	    		
          		  guardar(registro);
          		  bot.kill();
          	    }
                break;

            case STUCK:    	
                break;

            case STOPPED:
                break;
            default :
            	break;
        }
    }

    protected NavPoint buscarNavPoint(String str) {
    	List<NavPoint> lstpts = collectionToList(getWorldView().getAll(NavPoint.class).values());
    	for(NavPoint p:lstpts) {
  		  if(p.getId().getStringId().equals(str)) {
  			  return(p);
  		  }  		
  	  	}
  		  return null;
    }
    
    protected NavPoint getGuardingNavPoint() {
    	List<NavPoint> lstpts = collectionToList(getWorldView().getAll(NavPoint.class).values());
    	int i=0;
  	  	for(NavPoint p:lstpts) {
  		  if(p.getId().toString().equals("WorldObjectId[Test_Environment.PathNode30]")) {
  			  System.out.println(i);
  			  return(p);
  		  }
  		  i++;
  	  }
  	  	return(null);
    }

	public Graph reconstruirGrafo() {
		Graph g= new Graph(40);
		
		for(int i=0;i<39;i++) {mapa.put("Test_Environment.PathNode"+i, i);}
		mapa.put("Test_Environment.PlayerStart3",39);
		
		List<NavPoint> listaNodos = collectionToList(getWorldView().getAll(NavPoint.class).values());
		for(NavPoint np : listaNodos) {
			for(NavPointNeighbourLink nn: np.getOutgoingEdges().values()) {
				g.addEdge(mapa.get(np.getId().getStringId()), mapa.get(nn.getId().getStringId()));
			}
		}
		return g;
	}
  

    public static void main(String args[]) throws PogamutException {
        new UT2004BotRunner(CausalBOT.class, "Causal_BOT").setMain(true).setLogLevel(Level.WARNING).startAgent();
    }
    

    
 public class Graph {
  
     // No. of vertices in graph
     private int v;
  
     // adjacency list
     private ArrayList<Integer>[] adjList;
  
     // Constructor
     public Graph(int vertices)
     {
         this.v = vertices;
         initAdjList();
     }

     @SuppressWarnings("unchecked")
     private void initAdjList()
     {
         adjList = new ArrayList[v];
         for (int i = 0; i < v; i++) {
             adjList[i] = new ArrayList<Integer>();
         }
     }
  
     public void addEdge(int u, int v)
     {
         adjList[u].add(v);
     }
     
     public ArrayList<ArrayList<Integer>> printAllPaths(int s, int d)
     {
         boolean[] isVisited = new boolean[v];
         ArrayList<ArrayList<Integer>> cams = new ArrayList<ArrayList<Integer>>();
         
         ArrayList<Integer> pathList = new ArrayList<Integer>();
  
         pathList.add(s);         
         printAllPathsUtil(s, d, isVisited, pathList, cams);
//         System.out.println("Los caminos son:");
//         int i=1;
//         String cad="";
//         for(ArrayList<Integer> e: cams) {
//        	 System.out.println("Camino "+i+": "+e);
//        	 cad+=""+e.toString()+"\n";
//        	 i++;        	         	 
//         }         
         //guardar(cad);
         return cams;
     }

     public void guardar(String arr) {
    	 try {
    	      FileWriter myWriter = new FileWriter("C:\\output.txt");
    	      myWriter.write(arr);
    	      myWriter.close();
    	    } catch (IOException e) {
    	    }
     }
     private void printAllPathsUtil(Integer u, Integer d,
                                    boolean[] isVisited,
                                    ArrayList<Integer> localPathList,
                                    ArrayList<ArrayList<Integer>> caminos)
     {
  
         if (u.equals(d)) {
        	 caminos.add(new ArrayList<Integer>(localPathList));
             return;
         }
         isVisited[u] = true;
         for (Integer i : adjList[u]) {
             if (!isVisited[i]) {
                 localPathList.add(i);
                 printAllPathsUtil(i, d, isVisited, localPathList,caminos);
                 localPathList.remove(i);
             }
         }
         isVisited[u] = false;
     }
 }
}

