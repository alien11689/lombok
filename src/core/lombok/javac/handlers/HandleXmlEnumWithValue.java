package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import static com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import lombok.XmlEnumWithValue;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.core.configuration.ConfigurationKey;
import lombok.core.configuration.FlagUsageType;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static com.sun.tools.javac.code.Flags.*;
import static lombok.core.handlers.HandlerUtil.handleFlagUsage;
import static lombok.javac.handlers.JavacHandlerUtil.chainDots;
import static lombok.javac.handlers.JavacHandlerUtil.deleteAnnotationIfNeccessary;
import static lombok.javac.handlers.JavacHandlerUtil.genJavaLangTypeRef;
import static lombok.javac.handlers.JavacHandlerUtil.genTypeRef;
import static lombok.javac.handlers.JavacHandlerUtil.injectMethod;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleXmlEnumWithValue extends JavacAnnotationHandler<XmlEnumWithValue> {

    public static final ConfigurationKey<FlagUsageType> XML_ENUM_WITH_VALUE_FLAG_USAGE
            = new ConfigurationKey<FlagUsageType>(
            "pl.touk.smx4.annotations.XmlEnumWithValue.flagUsage",
            "Emit a warning or error if @XmlEnumWithValue is used.") {
    };

    @Override
    public void handle(final AnnotationValues<XmlEnumWithValue> annotation, final JCTree.JCAnnotation ast, final JavacNode annotationNode) {
        handleFlagUsage(annotationNode, XML_ENUM_WITH_VALUE_FLAG_USAGE, "@XmlEnumWithValue");

        deleteAnnotationIfNeccessary(annotationNode, XmlEnumWithValue.class);

        //TODO check if has value field and is enum
//        TODO field name to configuration

        final JavacNode owner = annotationNode.up();
        switch (owner.getKind()) {
            case TYPE:
                handleClass(annotationNode);
                break;
            default:
                annotationNode.addError(
                        "@XmlEnumWithValue is legal only on enum with field value.");
                break;
        }
    }

    private void handleClass(JavacNode annotationNode) {
        generateValueMethod(annotationNode);
        generateFromValueMethod(annotationNode);
        JavacNode owner = annotationNode.up();
        System.out.println(owner);
    }

    private JCMethodDecl getConstructor(JavacNode classNode) {
        for (JavacNode field : classNode.down()) {
            if (field.getKind() == AST.Kind.METHOD) {
                if (field.getName().equals("<init>")) {
                    return (JCMethodDecl) field.get();
                }
            }
        }
        return null;
    }

    private void generateFromValueMethod(JavacNode annotationNode) {
        JavacNode owner = annotationNode.up();
        JavacTreeMaker maker = owner.getTreeMaker();

        JCMethodDecl constructor = getConstructor(owner);

        JCExpression returnType = genTypeRef(owner, owner.getPackageDeclaration() + "." + getTypeName(owner));
        List<JCTypeParameter> parameterTypes = List.nil();
        List<JCExpression> thrown = List.<JCExpression>nil();
        JCTree.JCThrow throwIllegalArgumentException = maker.Throw(maker.NewClass(null, List.<JCExpression>nil(), genJavaLangTypeRef(owner, "IllegalArgumentException"), List.<JCExpression>nil(), null));
        JCTree.JCEnhancedForLoop foreachEnumValues =
                maker.ForeachLoop(maker.VarDef(maker.Modifiers(0), owner.toName("cur"), returnType, null),
                        maker.Apply(List.<JCExpression>nil(), maker.Select(returnType, owner.toName("values")), List.<JCExpression>nil()),
                        maker.If(maker.Apply(List.<JCExpression>nil(), maker.Select(chainDots(owner, "cur", "value"), owner.toName("equals")), List.<JCExpression>of(maker.Ident(owner.toName("value")))),
                                maker.Return(maker.Ident(owner.toName("cur"))),
                                null));
        JCBlock body = maker.Block(0, List.<JCStatement>of(
                foreachEnumValues,
                throwIllegalArgumentException));
        JCTree.JCMethodDecl method = maker.MethodDef(
                maker.Modifiers(PUBLIC | STATIC),
                owner.toName("fromValue"),
                returnType,
                parameterTypes,
                constructor.params,
                thrown,
                body,
                null);

        injectMethod(owner, method);

    }

    private void generateValueMethod(JavacNode annotationNode) {
        JavacNode owner = annotationNode.up();
        JavacTreeMaker maker = owner.getTreeMaker();
        JCExpression returnType = genJavaLangTypeRef(owner, "String");
        JCBlock body = maker.Block(0, List.of((JCStatement) maker.Return(chainDots(owner, "this", "value"))));
        List<JCTypeParameter> parameterTypes = List.<JCTypeParameter>nil();
        List<JCVariableDecl> variable = List.<JCVariableDecl>nil();
        List<JCExpression> thrown = List.<JCExpression>nil();
        JCTree.JCMethodDecl method = maker.MethodDef(
                maker.Modifiers(PUBLIC),
                owner.toName("value"),
                returnType,
                parameterTypes,
                variable,
                thrown,
                body, null);

        injectMethod(owner, method);
    }

    public static String getTypeName(JavacNode typeNode) {
        String typeName = ((JCTree.JCClassDecl) typeNode.get()).name.toString();
        JavacNode upType = typeNode.up();
        while (upType.getKind() == AST.Kind.TYPE) {
            typeName = ((JCTree.JCClassDecl) upType.get()).name.toString() + "." + typeName;
            upType = upType.up();
        }
        return typeName;
    }

}
