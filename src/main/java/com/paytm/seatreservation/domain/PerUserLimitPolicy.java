package com.paytm.seatreservation.domain;

import com.paytm.seatreservation.exception.ApiException;
import com.paytm.seatreservation.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PerUserLimitPolicy implements ReservationPolicy {

    @Override
    public void validate(
            ReservationContext context
    ) {

        long requested =
                context.requestedSeats().size();

        long total =
                context.existingSeatCount()
                        + requested;

        if (total > context.show().getPerUserLimit()) {

            throw new ApiException(
                    ErrorCode.PER_USER_LIMIT,
                    "User booking limit exceeded"
            );
        }
    }
}