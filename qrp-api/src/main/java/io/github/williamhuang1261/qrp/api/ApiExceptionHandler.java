package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.core.MarketDataException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the two ways a request can be rejected -- a bad flag value from
 * {@link io.github.williamhuang1261.qrp.app.CliArguments#parse} or an
 * unknown symbol/strategy id from {@link io.github.williamhuang1261.qrp.app.BacktestRunner}
 * -- onto {@code 400}, carrying the exception's own message. Anything else is
 * left to Spring's default {@code 500} handling rather than swallowed here.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MarketDataException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleBadRequest(RuntimeException exception) {
        return new ApiError(exception.getMessage());
    }
}
