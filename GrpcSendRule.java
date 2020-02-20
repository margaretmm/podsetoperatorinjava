
public class GrpcSendRule {
    private final ManagedChannel channel;
    private final RuleReciverGrpc.RuleReciverBlockingStub blockingStub;
    private final RuleReciverGrpc.RuleReciverStub asyncStub;
    private static final Logger logger = Logger.getLogger(GrpcSendRule.class.getName());
    private Map<String, EventMapPojo> globelCurPushMap;
    private AtomicBoolean gSendRuleFlag;

    public GrpcSendRule(String host,int port,
                        Map<String, EventMapPojo> CurPushMap,
                        AtomicBoolean SendRuleFlag){
        channel = ManagedChannelBuilder.forAddress(host,port)
                .usePlaintext(true)
                .maxRetryAttempts(2)
                .idleTimeout(5L,TimeUnit.SECONDS)
                .build();

        blockingStub = RuleReciverGrpc.newBlockingStub(channel);
        asyncStub=RuleReciverGrpc.newStub(channel);
        globelCurPushMap=CurPushMap;
        gSendRuleFlag=SendRuleFlag;
    }


    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public  void sendSync(String ruleName, String podKey, RuleBody rule){
        String podName=podKey.split("/")[1];
        RuleRequest request = RuleRequest.newBuilder()
                .setRuleName(ruleName)
                .setPodName(podName)
                .setRuleBody(rule)
                .build();

        try{
                StreamObserver<RuleReply> cb=new StreamObserver<RuleReply>() {
                @Override
                public void onNext(RuleReply ruleReply) {
                    System.out.println("onNext 返回结果"+ruleReply.getRetCode());
                }

                @Override
                public void onError(Throwable throwable) {
                     logger.severe("Send rule to "+podKey+" err:"+throwable.getMessage());

                     try {
                          shutdown();
                     } catch (InterruptedException e) {
                          e.printStackTrace();
                     }
                     EventMapPojo pojo = globelCurPushMap.get(podKey);
                     if (null==pojo){//retry callback after remove in the first callback
                         return;
                     }
                     int rts=globelCurPushMap.get(podKey).getRetryTimes();
                     if (2<rts) {
                            globelCurPushMap.remove(podKey);
                     }
                }

                @Override
                public void onCompleted() {
                    System.out.println("onCompleted ");
                    globelCurPushMap.remove(podKey);
                    gSendRuleFlag.set(false);

                }
            };
            asyncStub.reciveRule(request,cb);
            globelCurPushMap.get(podKey).setRetryTimes(
                    globelCurPushMap.get(podKey).getRetryTimes()+1);

        } catch (StatusRuntimeException e)
        {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
    }

    public  int send(RuleBody rule){

        RuleRequest request = RuleRequest.newBuilder()
                .setRuleName("")
                .setPodName("")
                .setRuleBody(rule)
                .build();

        RuleReply response;
        try{
            response = blockingStub.reciveRule(request);
        } catch (StatusRuntimeException e)
        {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return -1;
        }
        logger.info("ret code: "+response.getRetCode());
        return response.getRetCode();
    }

//    public static void main(String[] args) throws InterruptedException {
//        GrpcSendRule client = new GrpcSendRule("127.0.0.1",50050,null);
//        try{
//
//            RuleBody body= RuleBody.newBuilder()
//                    .setMonitorApp("app S")
//                    .setMonitorType("log")
//                    .setMonitorFile("a.log")
//                    .setMonitorFileKeyword("error")
//                    .setMonitorPort(8080)
//                    .build();
//            client.send(body);
//        }finally {
//            client.shutdown();
//        }
//    }
}
