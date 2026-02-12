package top.asimov.pigeon.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.util.SaResult;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.asimov.pigeon.exception.BusinessException;

/**
 * Global Exception Handler
 */
@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String SQLITE_BUSY = "sqlite_busy";
  private static final String SQLITE_LOCKED = "database is locked";

  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * Handle custom business exceptions
   */
  @ExceptionHandler(BusinessException.class)
  public SaResult handleBusinessException(BusinessException e) {
    log.error("BusinessException: {}", e.getMessage());
    return SaResult.code(e.getCode()).setMsg(e.getMessage());
  }

  /**
   * Handle parameter validation exceptions
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public SaResult handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    BindingResult bindingResult = e.getBindingResult();
    String message = bindingResult.getFieldErrors()
        .stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining(", "));
    log.error("MethodArgumentNotValidException: {}", message);
    return SaResult.code(400).setMsg(message);
  }

  /**
   * Handle binding exceptions
   */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public SaResult handleBindException(BindException e) {
    BindingResult bindingResult = e.getBindingResult();
    String message = bindingResult.getFieldErrors()
        .stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining(", "));
    log.error("BindException: {}", message);
    return SaResult.code(400).setMsg(message);
  }

  /**
   * Handle all other runtime exceptions
   */
  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public SaResult handleRuntimeException(RuntimeException e) {
    if (isSqliteBusyException(e)) {
      log.warn("SQLite database is busy: {}", e.getMessage(), e);
      String message = messageSource.getMessage("database.busy", null,
          LocaleContextHolder.getLocale());
      return SaResult.error(message);
    }

    log.error("RuntimeException: {}", e.getMessage());
    return SaResult.error(e.getMessage());
  }

  /**
   * Handle not logged in exceptions
   *
   * @param e Not logged in exception
   * @return Not logged in error response
   */
  @ExceptionHandler(NotLoginException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public SaResult handleNotLoginException(NotLoginException e) {
    log.error("NotLoginException: {}", e.getMessage());
    return SaResult.error(e.getMessage());
  }

  /**
   * Handle not permission exceptions
   *
   * @param e Not permission exception
   * @return Not permission error response
   */
  @ExceptionHandler(NotPermissionException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public SaResult handleNotPermissionException(NotPermissionException e) {
    log.error("NotPermissionException: {}", e.getMessage());
    return SaResult.error(e.getMessage());
  }

  private boolean isSqliteBusyException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String lowerCaseMessage = message.toLowerCase(Locale.ROOT);
        if (lowerCaseMessage.contains(SQLITE_BUSY)
            || lowerCaseMessage.contains(SQLITE_LOCKED)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

}
