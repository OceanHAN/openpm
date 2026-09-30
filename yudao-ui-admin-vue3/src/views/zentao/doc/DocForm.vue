<template>
  <!-- 文档/章节 新建与编辑 -->
  <el-dialog v-model="visible" :title="dialogTitle" width="760px" append-to-body @closed="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="文档库" prop="lib">
        <el-select v-model="form.lib" class="w-full" :disabled="isEdit" @change="onLibChange">
          <el-option v-for="lib in libList" :key="lib.id" :label="lib.name" :value="lib.id!" />
        </el-select>
        <div v-if="isEdit" class="text-12px text-gray-400">
          换库请用列表里的「移动」操作，避免章节树结构被改乱
        </div>
      </el-form-item>

      <el-form-item label="上级章节">
        <el-tree-select
          v-model="form.parent"
          :data="chapterTree"
          :props="{ label: 'title', value: 'id', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          class="w-full"
          placeholder="不选则挂在库根"
        />
      </el-form-item>

      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入标题" maxlength="255" />
      </el-form-item>

      <el-form-item label="类型" prop="type">
        <el-select v-model="form.type" class="w-full" @change="onTypeChange">
          <el-option v-for="o in DOC_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <div v-if="form.type === 'chapter'" class="text-12px text-gray-400">
          章节是目录节点，不写正文；文档可以挂在章节下
        </div>
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio-button
            v-for="o in DOC_STATUS_OPTIONS"
            :key="o.value"
            :value="o.value"
          >
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="text-12px text-gray-400">
          草稿存在 version=0 的草稿位里，反复保存都不会产生版本；点「发布」才升成 v1
        </div>
      </el-form-item>

      <el-form-item v-if="form.type === 'url'" label="链接地址" prop="content">
        <el-input v-model="form.content" placeholder="https://www.zentao.net/ 或 www.zentao.net" />
      </el-form-item>

      <el-form-item v-else-if="needsContent(form.type)" label="正文">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="10"
          :placeholder="form.type === 'markdown' ? '# 标题（Markdown）' : '正文内容'"
        />
      </el-form-item>

      <el-form-item v-else-if="form.type === 'attachment'" label="附件编号">
        <el-input v-model="form.files" placeholder="附件编号，逗号分隔（先上传附件再填 id）" />
        <div class="text-12px text-gray-400">
          与「附件」模块配合：上传拿到 zt_file.id 后填这里
        </div>
      </el-form-item>

      <el-form-item label="关键词">
        <el-input v-model="form.keywords" placeholder="逗号分隔，用于检索" maxlength="255" />
      </el-form-item>

      <el-form-item label="权限">
        <el-select v-model="form.acl" class="!w-200px">
          <el-option label="公开" value="open" />
          <el-option label="私有" value="private" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as DocApi from '@/api/zentao/doc'
import {
  DOC_TYPE_OPTIONS,
  DOC_STATUS_OPTIONS,
  needsContent
} from './constants'

defineOptions({ name: 'ZentaoDocForm' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref()
const libList = ref<DocApi.DocLibVO[]>([])
const chapterTree = ref<DocApi.DocVO[]>([])

const form = ref<DocApi.DocVO>({
  id: undefined,
  lib: undefined,
  parent: 0,
  title: '',
  type: 'html',
  status: 'normal',
  content: '',
  keywords: '',
  acl: 'open'
})

const rules = {
  lib: [{ required: true, message: '请选择文档库', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  const action = isEdit.value ? '编辑' : '新建'
  return `${action}${form.value.type === 'chapter' ? '章节' : '文档'}`
})

const loadChapterTree = async (lib?: number) => {
  if (!lib) {
    chapterTree.value = []
    return
  }
  chapterTree.value = await DocApi.getChapterTree(lib)
}

const onLibChange = async (lib: number) => {
  // 换库后原章节必然失效，清掉并按新库重新拉树
  form.value.parent = 0
  await loadChapterTree(lib)
}

const onTypeChange = (type: string) => {
  // 章节没有正文，切过去时把正文清掉，避免提交一个无用字段
  if (type === 'chapter') {
    form.value.content = ''
    form.value.status = 'normal'
  }
}

/**
 * 打开弹窗
 *
 * libs 由列表页传进来 —— 列表页本来就要把所有库拉全（顶部筛选也要用），
 * 弹窗里再查一遍纯属浪费，而且会把「有哪些库」的逻辑写两份。
 */
const open = async (options: {
  type: 'create' | 'edit'
  row?: DocApi.DocVO
  libs: DocApi.DocLibVO[]
  defaultLib?: number
  defaultParent?: number
  /** 入口预设的类型（列表页的「新建章节」用它，缺省 html） */
  defaultType?: string
}) => {
  visible.value = true
  isEdit.value = options.type === 'edit'
  libList.value = options.libs

  if (options.type === 'edit' && options.row) {
    const detail = await DocApi.getDoc(options.row.id!, 0)
    form.value = {
      ...detail,
      // 后端按版本叠加后的正文在 content 里；编辑时回填成表单字段
      status: detail.status ?? 'normal'
    }
    await loadChapterTree(detail.lib)
  } else {
    form.value = {
      lib: options.defaultLib,
      parent: options.defaultParent ?? 0,
      title: '',
      type: options.defaultType ?? 'html',
      status: 'normal',
      content: '',
      keywords: '',
      acl: 'open'
    }
    await loadChapterTree(options.defaultLib)
  }
}
defineExpose({ open })

const reset = () => {
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await DocApi.updateDoc(form.value)
      message.success('修改成功')
    } else {
      await DocApi.createDoc(form.value)
      message.success('新建成功')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>
