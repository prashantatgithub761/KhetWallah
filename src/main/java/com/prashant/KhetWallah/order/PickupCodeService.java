package com.prashant.KhetWallah.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class PickupCodeService {
    private final OrderRepository orders;
    private final PickupCodeRepository codes;
    private final PasswordEncoder encoder;
    private final OrderEmailNotifications updates;
    private final Clock clock;
    private final SecureRandom random;
    @Autowired public PickupCodeService(OrderRepository orders,PickupCodeRepository codes,
            PasswordEncoder encoder,OrderEmailNotifications updates){this(orders,codes,encoder,updates,Clock.systemUTC(),new SecureRandom());}
    PickupCodeService(OrderRepository orders,PickupCodeRepository codes,PasswordEncoder encoder,
            OrderEmailNotifications updates,Clock clock,SecureRandom random){
        this.orders=orders;this.codes=codes;this.encoder=encoder;this.updates=updates;this.clock=clock;this.random=random;
    }
    @Transactional public Challenge issue(Long id,String email){
        OrderEntity order=locked(id);
        if(!order.getBuyer().getEmail().equals(email))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the buyer can generate a pickup code");
        requireReady(order);
        Instant now=clock.instant();
        PickupCode code=codes.findById(id).orElseGet(()->new PickupCode(id,now));
        code.refresh(now);
        if(code.getSendCount()>=5||code.getFailedAttempts()>=5)throw limited();
        if(now.isBefore(code.getResendAt()))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait 60 seconds before generating another pickup code");
        String plain=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        code.issue(encoder.encode(plain),order.getPickupRevision(),now);
        codes.saveAndFlush(code);
        return new Challenge(plain,code.getExpiresAt(),60);
    }
    @Transactional(noRollbackFor=InvalidPickupCodeException.class)
    public void confirm(Long id,String email,String plain){
        OrderEntity order=locked(id);
        if(order.getListing().getOwner()==null||!order.getListing().getOwner().getEmail().equals(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only this listing's farmer can confirm the handover");
        if(order.getStatus()==OrderStatus.COMPLETED)return;
        requireReady(order);
        PickupCode code=codes.findById(id).orElseThrow(InvalidPickupCodeException::new);
        Instant now=clock.instant();code.refresh(now);
        if(code.getFailedAttempts()>=5)throw limited();
        if(code.getConsumedAt()!=null||code.getExpiresAt()==null||!now.isBefore(code.getExpiresAt())
                ||code.getPickupRevision()!=order.getPickupRevision())throw new InvalidPickupCodeException();
        if(plain==null||!plain.matches("[0-9]{6}")||!encoder.matches(plain,code.getCodeHash())){
            code.fail();codes.saveAndFlush(code);throw new InvalidPickupCodeException();
        }
        order.complete();code.consume(now);codes.saveAndFlush(code);
        updates.queue(order,OrderEmailNotifications.Event.COMPLETED);
    }
    private OrderEntity locked(Long id){return orders.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));}
    private void requireReady(OrderEntity order){if(order.getStatus()!=OrderStatus.ACCEPTED||order.getPickupAt()==null
            ||order.getPickupInstructions()==null||order.getPickupInstructions().isBlank())
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Pickup details must be arranged for an accepted order first");}
    private ResponseStatusException limited(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many pickup-code attempts. Try again after one hour");}
    public record Challenge(String code,Instant expiresAt,int resendAfterSeconds){@Override public String toString(){return "PickupChallenge[REDACTED]";}}
}
