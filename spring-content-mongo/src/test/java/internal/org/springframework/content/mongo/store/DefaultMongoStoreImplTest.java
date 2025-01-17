package internal.org.springframework.content.mongo.store;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.JustBeforeEach;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.repository.StoreAccessException;
import org.springframework.content.commons.utils.PlacementService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import com.mongodb.client.gridfs.model.GridFSFile;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Ginkgo4jRunner.class)
@PrepareForTest({ GridFsTemplate.class, GridFSFile.class })
public class DefaultMongoStoreImplTest {
    private DefaultMongoStoreImpl<Object, String> mongoContentRepoImpl;
    private GridFsTemplate gridFsTemplate;
    private GridFSFile gridFSFile;
    private ObjectId gridFSId;
    private ContentProperty property;
    private GridFsResource resource;
    private Resource inputResource;
    private PlacementService placer;

    private InputStream content;
    private InputStream result;
    private Exception e;

    {
        Describe("DefaultMongoStoreImpl", () -> {

            Describe("AssociativeStore", () -> {
                BeforeEach(() -> {
                    placer = mock(PlacementService.class);
                    gridFsTemplate = mock(GridFsTemplate.class);
                    resource = mock(GridFsResource.class);
                    mongoContentRepoImpl = new DefaultMongoStoreImpl<Object, String>(
                            gridFsTemplate, null, placer);
                });
                Context("when the entity has a String-arg constructor - Issue #57", () ->{
                    BeforeEach(() -> {
                        mongoContentRepoImpl = new DefaultMongoStoreImpl<>(gridFsTemplate, null, placer);

                        property = new TestEntity();
                    });
                    It("should not call the placement service by default", () -> {
                        verify(placer, never()).convert(property, String.class);
                    });
                });

                Context("#unassociate", () -> {
                    BeforeEach(() -> {
                        property = new TestEntity();
                        property.setContentId("12345");
                    });
                    JustBeforeEach(() -> {
                        mongoContentRepoImpl.unassociate(property);
                    });
                    Context("when the entity has a shared @Id @ContentId field", () -> {
                        BeforeEach(() -> {
                            property = new SharedIdContentIdEntity();
                            property.setContentId("12345");
                        });
                        It("should not reset the entity's @ContentId (because it is also the @Id)", () -> {
                            assertThat(property.getContentId(), is("12345"));
                        });
                    });
                });
            });

            Describe("ContentStore", () -> {
                BeforeEach(() -> {
                    placer = mock(PlacementService.class);
                    gridFsTemplate = mock(GridFsTemplate.class);
                    gridFSFile = mock(GridFSFile.class);
                    resource = mock(GridFsResource.class);
                    mongoContentRepoImpl = spy(new DefaultMongoStoreImpl<Object, String>(gridFsTemplate, null, placer));
                });

                Context("#setContent", () -> {
                    BeforeEach(() -> {
                        property = new TestEntity();

                        content = mock(InputStream.class);
                    });

                    JustBeforeEach(() -> {
                        try {
                            mongoContentRepoImpl.setContent(property, content);
                        } catch (Exception e) {
                            this.e = e;
                        }
                    });

                    Context("#when the content already exists", () -> {
                        BeforeEach(() -> {
                            property.setContentId("abcd-efghi");

                            when(placer.convert(eq("abcd-efghi"), eq(String.class))).thenReturn("abcd-efghi");
                            when(gridFsTemplate.getResource("abcd-efghi")).thenReturn(resource);
                            when(resource.exists()).thenReturn(true);
                            when(resource.contentLength()).thenReturn(1L);
                        });

                        Context("when the gridfs store throws an exception", () -> {
                            BeforeEach(() -> {
                                when(gridFsTemplate.store(anyObject(), anyString())).thenThrow(new RuntimeException("set-exception"));
                            });
                            It("should throw a StoreAccessException", () -> {
                                assertThat(e, is(instanceOf(StoreAccessException.class)));
                                assertThat(e.getCause().getMessage(), is("set-exception"));
                            });
                        });
                    });
                });

                Context("#setContent from Resource", () -> {

                    BeforeEach(() -> {
                        property = new TestEntity();
                        content = new ByteArrayInputStream("Hello content world!".getBytes());
                        inputResource = new InputStreamResource(content);
                    });

                    JustBeforeEach(() -> {
                        try {
                            mongoContentRepoImpl.setContent(property, inputResource);
                        } catch (Exception e) {
                            this.e = e;
                        }
                    });

                    It("should delegate", () -> {
                        verify(mongoContentRepoImpl).setContent(eq(property), eq(content));
                    });

                    Context("when the resource throws an IOException", () -> {
                        BeforeEach(() -> {
                            inputResource = mock(Resource.class);
                            when(inputResource.getInputStream()).thenThrow(new IOException("setContent badness"));
                        });
                        It("should throw a StoreAccessException", () -> {
                            assertThat(e, is(instanceOf(StoreAccessException.class)));
                            assertThat(e.getMessage(), containsString("setContent badness"));
                        });
                    });
                });

                Context("#getContent", () -> {
                    BeforeEach(() -> {
                        property = new TestEntity();
                        property.setContentId("abcd");

                        content = mock(InputStream.class);

                        when(placer.convert(eq("abcd"), eq(String.class)))
                                .thenReturn("abcd");
                        when(gridFsTemplate.getResource("abcd")).thenReturn(resource);
                        when(resource.getInputStream()).thenReturn(content);
                    });

                    JustBeforeEach(() -> {
                        try {
                            result = mongoContentRepoImpl.getContent(property);
                        } catch (Exception e) {
                            this.e = e;
                        }
                    });

                    Context("when the resource exists", () -> {
                        BeforeEach(() -> {
                            when(resource.exists()).thenReturn(true);
                        });

                        Context("when the resource outputstream throws an IOException", () -> {
                            BeforeEach(() -> {
                                when(resource.getInputStream()).thenThrow(new IOException("get-ioexception"));
                            });
                            It("should throw a StoreAccessException", () -> {
                                assertThat(e, is(instanceOf(StoreAccessException.class)));
                                assertThat(e.getCause().getMessage(), is("get-ioexception"));
                            });
                        });
                    });
                });

                Context("#unsetContent", () -> {
                    BeforeEach(() -> {
                        property = new TestEntity();
                        property.setContentId("abcd");

                        when(placer.convert(eq("abcd"), eq(String.class)))
                                .thenReturn("abcd");
                        when(gridFsTemplate.getResource("abcd")).thenReturn(resource);
                        when(resource.exists()).thenReturn(true);
                    });

                    JustBeforeEach(() -> {
                        try {
                            mongoContentRepoImpl.unsetContent(property);
                        } catch (Exception e) {
                            this.e = e;
                        }
                    });

                    Context("when gridfs deletion throws an exception", () -> {
                        BeforeEach(() -> {
                            doThrow(new RuntimeException("unset-exception")).when(gridFsTemplate).delete(anyObject());
                        });
                        It("should throw a StoreAccessException", () -> {
                            assertThat(e, is(instanceOf(StoreAccessException.class)));
                            assertThat(e.getCause().getMessage(), is("unset-exception"));
                        });
                    });
                });
            });
        });
    }

    @Test
    public void test() {
        // noop
    }

    public interface ContentProperty {
        String getContentId();

        void setContentId(String contentId);

        long getContentLen();

        void setContentLen(long contentLen);
    }

    public static class TestEntity implements ContentProperty {

        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public TestEntity() {
            this.contentId = null;
        }

        public TestEntity(String contentId) {
            this.contentId = new String(contentId);
        }

        @Override
        public String getContentId() {
            return this.contentId;
        }

        @Override
        public void setContentId(String contentId) {
            this.contentId = contentId;
        }

        @Override
        public long getContentLen() {
            return contentLen;
        }

        @Override
        public void setContentLen(long contentLen) {
            this.contentLen = contentLen;
        }
    }

    public static class SharedIdContentIdEntity implements ContentProperty {

        @javax.persistence.Id
        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public SharedIdContentIdEntity() {
            this.contentId = null;
        }

        @Override
        public String getContentId() {
            return this.contentId;
        }

        @Override
        public void setContentId(String contentId) {
            this.contentId = contentId;
        }

        @Override
        public long getContentLen() {
            return contentLen;
        }

        @Override
        public void setContentLen(long contentLen) {
            this.contentLen = contentLen;
        }
    }

    public static class SharedSpringIdContentIdEntity implements ContentProperty {

        @org.springframework.data.annotation.Id
        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public SharedSpringIdContentIdEntity() {
            this.contentId = null;
        }

        @Override
        public String getContentId() {
            return this.contentId;
        }

        @Override
        public void setContentId(String contentId) {
            this.contentId = contentId;
        }

        @Override
        public long getContentLen() {
            return contentLen;
        }

        @Override
        public void setContentLen(long contentLen) {
            this.contentLen = contentLen;
        }
    }
}