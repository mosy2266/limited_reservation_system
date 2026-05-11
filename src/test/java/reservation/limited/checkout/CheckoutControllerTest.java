package reservation.limited.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void getCheckoutReturnsProductAndUserPoint() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, 1, 2));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", product.getId())
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.data.product.id", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data.product.name", is("테스트 상품")))
                .andExpect(jsonPath("$.data.product.price", is(100000)))
                .andExpect(jsonPath("$.data.user.id", is(1)))
                .andExpect(jsonPath("$.data.user.availablePoint", is(30000)));
    }

    @Test
    void getCheckoutReturnsNotFoundWhenProductDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 999L)
                        .param("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getCheckoutReturnsConflictWhenProductIsUnavailable() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.UNAVAILABLE, 1, 2));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", product.getId())
                        .param("userId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_AVAILABLE")));
    }

    @Test
    void getCheckoutReturnsGoneWhenSaleIsClosed() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -3, -1));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", product.getId())
                        .param("userId", "1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("SALE_CLOSED")));
    }

    @Test
    void getCheckoutReturnsBadRequestWhenUserIdIsMissing() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, 1, 2));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", product.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    @Test
    void getCheckoutReturnsBadRequestWhenUserIdIsInvalid() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, 1, 2));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", product.getId())
                        .param("userId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    private Product newProduct(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays) {
        LocalDateTime now = LocalDateTime.now();
        return new Product(
                "테스트 상품",
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.plusDays(saleOpenOffsetDays),
                now.plusDays(saleCloseOffsetDays),
                status
        );
    }
}
