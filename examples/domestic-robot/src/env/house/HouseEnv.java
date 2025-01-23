package house;

import jason.asSyntax.*;
import jason.environment.grid.Location;
import java.util.logging.Logger;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class HouseEnv extends Artifact {

    // common literals
    // // public static final Literal of  = Literal.parseLiteral("open(fridge)");
    // // public static final Literal clf = Literal.parseLiteral("close(fridge)");
    // // public static final Literal gb  = Literal.parseLiteral("get(beer)");
    // // public static final Literal hb  = Literal.parseLiteral("hand_in(beer)");
    // // public static final Literal sb  = Literal.parseLiteral("sip(beer)");
    // // public static final Literal hob = Literal.parseLiteral("has(owner,beer)");

    // // public static final Literal af = Literal.parseLiteral("at(robot,fridge)");
    // // public static final Literal ao = Literal.parseLiteral("at(robot,owner)");
    public static final Literal at_l = ASSyntax.createLiteral("at");
    public static final Literal robot_l = ASSyntax.createLiteral("robot");
    public static final Literal owner_l = ASSyntax.createLiteral("owner");
    public static final Literal beer_l = ASSyntax.createLiteral("beer");
    public static final Literal fridge_l = ASSyntax.createLiteral("fridge");
    public static final Literal stock_l = ASSyntax.createLiteral("stock");
    public static final Literal has_l = ASSyntax.createLiteral("has");

    static Logger logger = Logger.getLogger(HouseEnv.class.getName());

    HouseModel model = null; // the model of the grid

    @OPERATION
    void init( String arg ) {
        model = new HouseModel();
        if ( arg.equals( "gui" ) ) {
            HouseView view  = new HouseView( model );
            model.setView(view);
        }
        updatePercepts();
    }

    /** creates the agents percepts based on the HouseModel */
    void updatePercepts() {
        // // // clear the percepts of the agents
        // // clearPercepts("robot");
        // // clearPercepts("owner");

        // get the robot location
        Location lRobot = model.getAgPos(0);

        // // ObsProperty robot_p = getObsProperty( "robot" );
        // add agent location to its percepts
        if (lRobot.equals(model.lFridge)) {
            // // addPercept("robot", af);
            // // robot_p.updateValue( af );
            signal( "robot", at_l, robot_l, fridge_l);
        }
        if (lRobot.equals(model.lOwner)) {
            // // addPercept("robot", ao);
            // // robot_p.updateValue( ao );
            signal( "robot", at_l, robot_l, owner_l);
        }

        // add beer "status" the percepts
        if (model.fridgeOpen) {
            // // addPercept("robot", Literal.parseLiteral("stock(beer,"+model.availableBeers+")"));
            // // robot_p.updateValue(Literal.parseLiteral("stock(beer,"+model.availableBeers+")"));
            // signal("robot(stock(beer," + model.availableBeers + "))");
            signal("robot", stock_l, beer_l, model.availableBeers);
        }
        if (model.sipCount > 0) {
            // // addPercept("robot", hob);
            // // robot_p.updateValue( hob );
            signal( "robot", has_l, owner_l, beer_l);

            // // addPercept("owner", hob);
            // // getObsProperty( "owner" ).updateValue( hob );
            signal( "owner", has_l, owner_l, beer_l);
        }
    }

    @OPERATION
    public void open( String object ){
        if ( object.equals( "fridge" ) )
            if ( model.openFridge() )
                updatePercepts();
    }

    @OPERATION
    public void close( String object ){
        if ( object.equals( "fridge" ) )
            if( model.closeFridge() )
                updatePercepts();
    }

    @OPERATION
    public void move_towards( String dest_str ){
        Location dest = null;
        if ( dest_str.equals( "fridge" ) )
            dest = model.lFridge;
        else if ( dest_str.equals("owner") )
            dest = model.lOwner;
        try {
            if ( model.moveTowards( dest ) )
                updatePercepts();
        } catch ( Exception e ){
            e.printStackTrace();
        }
    }

    @OPERATION
    public void get( String object ){
            if ( object.equals("beer") )
                if ( model.getBeer() )
                    updatePercepts();
    } 

    @OPERATION
    public void hand_in( String object ) {
            if ( object.equals( "beer" ) )
                if ( model.handInBeer() )
                    updatePercepts();
    }

    @OPERATION
    public void sip( String object ) {
            if ( object.equals("beer") )
                if ( model.sipBeer() )
                    updatePercepts();
    }

    @OPERATION
    public void deliver( String product, int quantity ) {
        if ( product.equals( "beer" ) ) {
            try{
                Thread.sleep(4000);
                if ( model.addBeer( quantity ) )
                    updatePercepts();
            } catch ( Exception e ){
                logger.info("Failed to execute action deliver!"+e);
            }
        }
    }

    // // @Orride
    // // public boolean executeAction(String ag, Structure action) {
    // //     System.out.println("["+ag+"] doing: "+action);
    // //     boolean result = false;
    // //     if (action.equals(of)) { // of = open(fridge)
    // //         result = model.openFridge();

    // //     } else if (action.equals(clf)) { // clf = close(fridge)
    // //         result = model.closeFridge();

    // //     } else if (action.getFunctor().equals("move_towards")) {
    // //         String l = action.getTerm(0).toString();
    // //         Location dest = null;
    // //         if (l.equals("fridge")) {
    // //             dest = model.lFridge;
    // //         } else if (l.equals("owner")) {
    // //             dest = model.lOwner;
    // //         }

    // //         try {
    // //             result = model.moveTowards(dest);
    // //         } catch (Exception e) {
    // //             e.printStackTrace();
    // //         }

    // //     } else if (action.equals(gb)) {
    // //         result = model.getBeer();

    // //     } else if (action.equals(hb)) {
    // //         result = model.handInBeer();

    // //     } else if (action.equals(sb)) {
    // //         result = model.sipBeer();

    // //     } else if (action.getFunctor().equals("deliver")) {
    // //         // wait 4 seconds to finish "deliver"
    // //         try {
    // //             Thread.sleep(4000);
    // //             result = model.addBeer( (int)((NumberTerm)action.getTerm(1)).solve());
    // //         } catch (Exception e) {
    // //             logger.info("Failed to execute action deliver!"+e);
    // //         }

    // //     } else {
    // //         logger.info("Failed to execute action "+action);
    // //     }

    // //     if (result) {
    // //         updatePercepts();
    // //         try {
    // //             Thread.sleep(100);
    // //         } catch (Exception e) {}
    // //     }
    // //     return result;
    // // }
}
