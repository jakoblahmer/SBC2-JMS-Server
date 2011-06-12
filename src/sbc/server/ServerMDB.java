package sbc.server;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import sbc.model.ChocolateRabbit;
import sbc.model.Egg;
import sbc.model.Nest;
import sbc.model.Product;



/**
 * Message Driven Bean - acts as server
 * 
 * receives all ingredients and forwards them
 *
 */
@MessageDriven(
	activationConfig = { @ActivationConfigProperty(
			propertyName = "destinationType", propertyValue = "javax.jms.Queue"
	) },
mappedName = "sbc.server.queue")
public class ServerMDB implements MessageListener {

	// ids
	private static int eggCounter = 1;

	private static int chocoCounter = 1;

	private static int nestCounter = 1;

	// queues
	@Resource(mappedName = "sbc.gui.queue")
	private Queue guiQueue;

	@Resource(mappedName = "sbc.color.queue")
	private Queue colorQueue;

	@Resource(mappedName = "sbc.build.queue")
	private Queue buildQueue;

	@Resource(mappedName = "sbc.logistic.queue")
	private Queue logisticQueue;

	// connection
	@Resource(mappedName = "SBC.Factory")
	private ConnectionFactory connectionFactory;

	private Connection connection;

	private Session session;

	// message producer
	private MessageProducer buildProducer;
	private MessageProducer colorProducer;
	private MessageProducer logisticProducer;
	private MessageProducer guiProducer;

	// tmp vars (for communication with gui)
	private int guiEggCount;
	private int guiEggColorCount;
	private int guiChocoCount;
	private int guiNestCount;
	private int guiNestCompletedCount;
	private Nest guiObject;

	/**
	 * Default constructor. 
	 */
	public ServerMDB() {}


	/**
	 * - initiates the connection
	 * - creates the queue producers
	 */
	@PostConstruct
	public void postConstruct()	{

		try {
			connection = connectionFactory.createConnection();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			buildProducer = session.createProducer(buildQueue);
			colorProducer = session.createProducer(colorQueue);
			logisticProducer = session.createProducer(logisticQueue);
			guiProducer = session.createProducer(guiQueue);
		} catch (JMSException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @see MessageListener#onMessage(Message)
	 * 
	 * called, when a message receives
	 * 
	 */
	public void onMessage(Message message) {

		Message guiMessage = null;

		guiEggCount = guiEggColorCount = guiChocoCount = guiNestCount = guiNestCompletedCount = 0;
		guiObject = null;
		Message replyMsg = null;

		// check if message is object message
		if(message instanceof ObjectMessage)	{
			ObjectMessage om = (ObjectMessage) message;
			try {
				if(om.getObject() instanceof Product)	{
					
					if(om.getObject() instanceof Egg)	{
						// got an egg, check if colored or not to proceed
						Egg egg = (Egg) om.getObject();

						if(egg.isColored())	{
							// put in BUILD QUEUE (colored eggs can be put into nests)

							guiEggColorCount = 1;
							guiEggCount = -1;
							
							// send egg to build queue
							replyMsg = session.createObjectMessage(egg);
							replyMsg.setStringProperty("product", "egg");
							buildProducer.send(replyMsg);
						} else	{
							// put in COLOR QUEUE
							// give egg an id
							if(!egg.hasId())	{
								egg.setId(eggCounter++);
							}
							
							// first time here
							if(egg.getColor().size() == 0)
								guiEggCount = 1;
							
							// put in color queue
							replyMsg = session.createObjectMessage(egg);
							replyMsg.setStringProperty("NOCOLOR", "1");
							colorProducer.send(replyMsg);
						}

					} else if(om.getObject() instanceof ChocolateRabbit)	{
						// got a chocolate bunny
						ChocolateRabbit rabbit = (ChocolateRabbit) om.getObject();

						// give id
						if(!rabbit.hasId())
							rabbit.setId(chocoCounter++);


						guiChocoCount = 1;

						// put in BUILD QUEUE
						replyMsg = session.createObjectMessage(rabbit);
						replyMsg.setStringProperty("product", "chocolateRabbit");
						buildProducer.send(replyMsg);

					}
				} else if(om.getObject() instanceof Nest)	{
					// got a nest
					Nest nest = (Nest) om.getObject();

					if(!nest.hasId())
						nest.setId(nestCounter++);

					if(!nest.isComplete())	{
						System.out.println("NEST (" + nest.getId() + ") IS NOT COMPLETED... ERROR!");
						return;
					}

					if(nest.isShipped())	{
						// add to DONE nests
						System.out.println("NEST IS COMPLETED & shipped!");
						System.out.println("##### DONE WITH NEST (" + nest.getId() + ")");

						guiNestCompletedCount = 1;
						guiNestCount = -1;
						guiObject = nest;
					} else	{
						guiEggColorCount = -2;
						guiChocoCount = -1;
						guiNestCount = 1;
						guiObject = nest;
						System.out.println("NEST IS COMPLETED & can be shipped...");
						replyMsg = session.createObjectMessage(nest);
						logisticProducer.send(replyMsg);
					}

				} else	{
					System.out.println("GOT NOTHING PARSABLE");
					return;
				}
				
				if(!message.propertyExists("hideFromGUI"))	{
				
					// send changes to gui
					guiMessage = session.createObjectMessage(guiObject);
					guiMessage.setIntProperty("eggCount", guiEggCount);
					guiMessage.setIntProperty("eggColorCount", guiEggColorCount);
					guiMessage.setIntProperty("chocoCount", guiChocoCount);
					guiMessage.setIntProperty("nestCount", guiNestCount);
					guiMessage.setIntProperty("nestCompletedCount", guiNestCompletedCount);
					
					guiProducer.send(guiMessage);
				}
			} catch (JMSException e) {
				System.out.println("Error parsing message...");
				e.printStackTrace();
			}
		} else	{
			System.out.println("Error parsing message...");
		}
	}

}
