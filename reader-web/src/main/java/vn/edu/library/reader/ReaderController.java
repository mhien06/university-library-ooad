package vn.edu.library.reader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.library.reader.service.ReaderAuthService;
import vn.edu.library.reader.service.ReaderLibraryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class ReaderController {
    private final ReaderAuthService auth;
    private final ReaderLibraryService library;
    private final Path storageRoot;
    private static final int MAX_FAILED=5;
    private static final long LOCK_MS=10*60*1000L;
    private final java.util.concurrent.ConcurrentHashMap<String,long[]> attempts=new java.util.concurrent.ConcurrentHashMap<>();

    public ReaderController(ReaderAuthService auth, ReaderLibraryService library,
                            @Value("${library.storage-directory:../library-storage}") String storageDirectory) {
        this.auth=auth; this.library=library; this.storageRoot=Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @ModelAttribute("csrfToken")
    public String csrf(HttpServletRequest request) { return WebConfiguration.csrf(request.getSession(true)); }

    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        model.addAttribute("readerName",session.getAttribute("fullName"));
        return "home";
    }

    @GetMapping("/rules")
    public String rules(HttpSession session, Model model) {
        model.addAttribute("readerName",session.getAttribute("fullName"));
        return "rules";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required=false) String expired, Model model) {
        if ("1".equals(expired)) model.addAttribute("notice","Phiên đăng nhập đã hết hạn hoặc tài khoản không còn hoạt động. Hãy đăng nhập lại.");
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,@RequestParam String password,HttpServletRequest request,Model model) {
        String key=(username==null?"":username.trim().toLowerCase())+"|"+request.getRemoteAddr();
        long now=System.currentTimeMillis();
        long[] st=attempts.get(key);
        if(st!=null&&st[0]>=MAX_FAILED&&now-st[1]<LOCK_MS){
            model.addAttribute("error","Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau "+Math.max(1,(LOCK_MS-(now-st[1]))/60000+1)+" phút.");return "login";
        }
        try {
            ReaderAuthService.ReaderIdentity user=auth.authenticate(username,password);
            if(user==null){
                attempts.merge(key,new long[]{1,now},(o,n)->new long[]{now-o[1]>=LOCK_MS?1:o[0]+1,now});
                model.addAttribute("error","Tên đăng nhập hoặc mật khẩu không đúng, tài khoản bị khóa, hoặc hồ sơ trường không còn hiệu lực.");return "login";}
            attempts.remove(key);
            request.changeSessionId();HttpSession session=request.getSession(true);
            session.setAttribute("accountId",user.accountId());session.setAttribute("memberId",user.memberId());
            session.setAttribute("fullName",user.fullName());session.setAttribute("username",user.username());
            session.setAttribute("csrfToken",WebConfiguration.csrf(session));
            return "redirect:/catalog";
        } catch(Exception ex){model.addAttribute("error","Không thể đăng nhập lúc này. Hãy thử lại.");return "login";}
    }

    @GetMapping("/register") public String registerPage(){return "register";}

    @PostMapping("/register")
    public String register(@RequestParam String memberCode,@RequestParam String email,@RequestParam String username,
                          @RequestParam String password,@RequestParam String confirmPassword,Model model) {
        if(password==null||!password.equals(confirmPassword)){model.addAttribute("error","Hai mật khẩu chưa khớp.");return "register";}
        try{auth.register(memberCode,email,username,password);model.addAttribute("notice","Tạo tài khoản thành công. Bạn có thể đăng nhập.");return "login";}
        catch(IllegalArgumentException ex){model.addAttribute("error",ex.getMessage());return "register";}
        catch(Exception ex){model.addAttribute("error","Tên đăng nhập/email có thể đã được sử dụng. Hãy kiểm tra thông tin rồi thử lại.");return "register";}
    }

    @PostMapping("/logout") public String logout(HttpSession session){session.invalidate();return "redirect:/";}

    @GetMapping("/catalog")
    public String catalog(@RequestParam(defaultValue="") String q,Model model,HttpSession session){
        model.addAttribute("books",library.findBooks(q));model.addAttribute("q",q);model.addAttribute("readerName",session.getAttribute("fullName"));return "catalog";
    }

    @PostMapping("/reservations")
    public String reserve(@RequestParam long bookId,HttpSession session,RedirectAttributes flash){
        try{library.reserve((Long)session.getAttribute("memberId"),bookId);flash.addFlashAttribute("notice","Đã gửi yêu cầu đặt trước. Thủ thư sẽ xem xét yêu cầu của bạn.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/catalog";
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancelReservation(@PathVariable long id,HttpSession session,RedirectAttributes flash){
        try{library.cancelReservation((Long)session.getAttribute("memberId"),id);flash.addFlashAttribute("notice","Đã hủy yêu cầu đặt trước.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/reservations";
    }

    @GetMapping("/reservations") public String reservations(HttpSession s,Model m){m.addAttribute("reservations",library.reservations((Long)s.getAttribute("memberId")));return "reservations";}
    @GetMapping("/loans") public String loans(HttpSession s,Model m){m.addAttribute("loans",library.loans((Long)s.getAttribute("memberId")));return "loans";}
    @GetMapping("/violations") public String violations(HttpSession s,Model m){m.addAttribute("violations",library.violations((Long)s.getAttribute("memberId")));return "violations";}
    @GetMapping("/documents") public String documents(@RequestParam(defaultValue="") String q,Model m){m.addAttribute("documents",library.documents(q));m.addAttribute("q",q);m.addAttribute("needsSearch",q==null||q.isBlank());return "documents";}

    @GetMapping("/account")
    public String account(HttpSession session,Model model){
        model.addAttribute("profile",auth.profile((Long)session.getAttribute("memberId")));
        model.addAttribute("username",session.getAttribute("username"));
        return "account";
    }

    @PostMapping("/account")
    public String updateAccount(@RequestParam String email,@RequestParam(required=false) String phone,HttpSession session,RedirectAttributes flash){
        try{auth.updateContact((Long)session.getAttribute("memberId"),email,phone);flash.addFlashAttribute("notice","Thông tin liên hệ đã được cập nhật.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        catch(Exception ex){flash.addFlashAttribute("error","Không thể cập nhật thông tin. Email có thể đã được hồ sơ khác sử dụng.");}
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword,@RequestParam String newPassword,@RequestParam String confirmPassword,
                                 HttpServletRequest request,HttpSession session,RedirectAttributes flash){
        try{
            auth.changePassword((Long)session.getAttribute("accountId"),currentPassword,newPassword,confirmPassword);
            request.changeSessionId();
            HttpSession rotated=request.getSession(false);
            if(rotated!=null){rotated.removeAttribute("csrfToken");WebConfiguration.csrf(rotated);}
            flash.addFlashAttribute("notice","Đổi mật khẩu thành công.");
        }catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/account";
    }

    @GetMapping("/cards")
    public String cards(HttpSession session,Model model){
        long memberId=(Long)session.getAttribute("memberId");
        model.addAttribute("cards",library.cards(memberId));model.addAttribute("canRequestCard",library.canRequestCard(memberId));
        return "cards";
    }

    @PostMapping("/cards/request")
    public String requestCard(@RequestParam String paymentMethod,@RequestParam(defaultValue="") String requestReason,HttpSession session,RedirectAttributes flash){
        try{String cardNumber=library.requestCard((Long)session.getAttribute("memberId"),paymentMethod,requestReason);
            String next="QR_SIMULATED".equals(paymentMethod)
                    ? " Đã mô phỏng thanh toán QR; không phát sinh giao dịch tiền thật. Thẻ đang chờ thủ thư giao tại quầy."
                    : " Thanh toán phí tại quầy thư viện; thẻ chỉ được kích hoạt sau khi thủ thư xác nhận thu phí và giao thẻ.";
            flash.addFlashAttribute("notice","Đã gửi yêu cầu cấp thẻ "+cardNumber+"."+next);}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/cards";
    }

    @PostMapping("/violations/{id}/pay")
    public String payViolation(@PathVariable long id,@RequestParam BigDecimal amount,HttpSession session,RedirectAttributes flash){
        try{String receipt=library.payViolationBySimulatedQr((Long)session.getAttribute("memberId"),id,amount);
            flash.addFlashAttribute("notice","Đã mô phỏng thanh toán QR. Biên nhận "+receipt+"; không phát sinh giao dịch tiền thật.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/violations";
    }

    @PostMapping("/loans/{id}/renew")
    public String renewLoan(@PathVariable long id,HttpSession session,RedirectAttributes flash){
        try{LocalDate due=library.renew((Long)session.getAttribute("memberId"),id);
            flash.addFlashAttribute("notice","Gia hạn thành công. Hạn trả mới: "+due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+".");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/loans";
    }

    @GetMapping("/documents/{id}/read")
    public ResponseEntity<Resource> readDocument(@PathVariable long id){
        Path path=library.documentPath(id,storageRoot);
        if(path==null||!Files.isRegularFile(path))return ResponseEntity.notFound().build();
        FileSystemResource resource=new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(path.getFileName().toString()).build().toString())
                .body(resource);
    }
}
